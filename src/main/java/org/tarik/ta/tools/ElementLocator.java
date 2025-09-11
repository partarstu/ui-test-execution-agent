/*
 * Copyright © 2025 Taras Paruta (partarstu@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tarik.ta.tools;

import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.Clusterable;
import org.apache.commons.math3.ml.clustering.DBSCANClusterer;
import org.apache.commons.math3.ml.distance.DistanceMeasure;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.AgentConfig;
import org.tarik.ta.dto.BoundingBox;
import org.tarik.ta.dto.UiElementIdentificationResult;
import org.tarik.ta.exceptions.UserChoseTerminationException;
import org.tarik.ta.exceptions.UserInterruptedExecutionException;
import org.tarik.ta.prompts.*;
import org.tarik.ta.rag.RetrieverFactory;
import org.tarik.ta.rag.UiElementRetriever;
import org.tarik.ta.rag.UiElementRetriever.RetrievedUiElementItem;
import org.tarik.ta.rag.model.UiElement;
import org.tarik.ta.rag.model.UiElement.Screenshot;
import org.tarik.ta.user_dialogs.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Thread.currentThread;
import static java.util.Collections.max;
import static java.util.Comparator.comparingDouble;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.UUID.randomUUID;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;
import static java.util.stream.Collectors.*;
import static java.util.stream.IntStream.range;
import static java.util.stream.Stream.concat;
import static javax.swing.JOptionPane.showMessageDialog;
import static org.tarik.ta.AgentConfig.getBboxIdentificationModelName;
import static org.tarik.ta.AgentConfig.getBboxIdentificationModelProvider;
import static org.tarik.ta.model.ModelFactory.getModel;
import static org.tarik.ta.model.ModelFactory.getVerificationVisionModel;
import static org.tarik.ta.rag.model.UiElement.Screenshot.fromBufferedImage;
import static org.tarik.ta.utils.BoundingBoxUtil.calculateIoU;
import static org.tarik.ta.utils.BoundingBoxUtil.drawBoundingBox;
import static org.tarik.ta.utils.BoundingBoxUtil.drawBoundingBoxes;
import static org.tarik.ta.utils.BoundingBoxUtil.mergeOverlappingRectangles;
import static org.tarik.ta.utils.CommonUtils.*;
import static org.tarik.ta.utils.ImageMatchingUtil.findMatchingRegionsWithORB;
import static org.tarik.ta.utils.ImageMatchingUtil.findMatchingRegionsWithTemplateMatching;
import static org.tarik.ta.utils.ImageUtils.*;
import static org.tarik.ta.utils.ScreenGridUtils.drawGrid;

public class ElementLocator extends AbstractTools {
    private static final Logger LOG = LoggerFactory.getLogger(ElementLocator.class);
    private static final double MIN_TARGET_RETRIEVAL_SCORE = AgentConfig.getElementRetrievalMinTargetScore();
    private static final double MIN_PAGE_RELEVANCE_SCORE = AgentConfig.getElementRetrievalMinPageRelevanceScore();
    private static final double MIN_GENERAL_RETRIEVAL_SCORE = AgentConfig.getElementRetrievalMinGeneralScore();
    private static final int USER_DIALOG_DISMISS_DELAY_MILLIS = 1000;
    private static final String BOUNDING_BOX_COLOR_NAME = AgentConfig.getElementBoundingBoxColorName();
    private static final Color BOUNDING_BOX_COLOR = getColorByName(BOUNDING_BOX_COLOR_NAME);
    private static final int TOP_N_ELEMENTS_TO_RETRIEVE = AgentConfig.getRetrieverTopN();
    private static final boolean UNATTENDED_MODE = AgentConfig.isUnattendedMode();
    private static final UiElementRetriever elementRetriever = RetrieverFactory.getUiElementRetriever();
    private static final boolean DEBUG_MODE = AgentConfig.isDebugMode();
    private static final int VISUAL_GROUNDING_MODEL_VOTE_COUNT = AgentConfig.getElementLocatorVisualGroundingModelVoteCount();
    private static final int VALIDATION_MODEL_VOTE_COUNT = AgentConfig.getElementLocatorValidationModelVoteCount();
    private static final double BBOX_CLUSTERING_MIN_INTERSECTION_RATIO = AgentConfig.getBboxClusteringMinIntersectionRatio();
    private static final int GRID_ROWS = AgentConfig.getElementGridRows();
    private static final int GRID_COLS = AgentConfig.getElementGridCols();

    private static final int ZOOM_SCALE_FACTOR = AgentConfig.getElementLocatorZoomScaleFactor();
    private static final double ZOOM_IN_EXTENSION_RATIO_PROPORTIONAL_TO_ELEMENT = 15.0;

    public static Optional<Rectangle> locateElementOnTheScreen(String elementDescription, String testSpecificData) {
        var retrievedElements = elementRetriever.retrieveUiElements(elementDescription, TOP_N_ELEMENTS_TO_RETRIEVE,
                MIN_GENERAL_RETRIEVAL_SCORE);
        var matchingByDescriptionUiElements = retrievedElements.stream()
                .filter(retrievedUiElementItem -> retrievedUiElementItem.mainScore() >= MIN_TARGET_RETRIEVAL_SCORE)
                .map(RetrievedUiElementItem::element)
                .toList();
        if (matchingByDescriptionUiElements.isEmpty() && !retrievedElements.isEmpty()) {
            return processNoElementsFoundInDbWithSimilarCandidatesPresentCase(elementDescription, retrievedElements, testSpecificData);
        } else if (matchingByDescriptionUiElements.isEmpty()) {
            return processNoElementsFoundInDbCase(elementDescription);
        } else {
            UiElement bestMatchingElement;
            if (matchingByDescriptionUiElements.size() > 1) {
                LOG.info("{} UI elements found in vector DB which semantically match the description '{}'. Scoring them based on " +
                        "the relevance to the currently opened page.", matchingByDescriptionUiElements.size(), elementDescription);
                var bestMatchingByDescriptionAndPageRelevanceUiElements =
                        getBestMatchingByDescriptionAndPageRelevanceUiElements(elementDescription);
                if (bestMatchingByDescriptionAndPageRelevanceUiElements.size() > 1) {
                    return processMultipleElementsRelevantToPageFoundCase(elementDescription,
                            bestMatchingByDescriptionAndPageRelevanceUiElements, retrievedElements, testSpecificData);
                } else if (bestMatchingByDescriptionAndPageRelevanceUiElements.isEmpty()) {
                    return processNoElementsRelevantToPageFoundCase(elementDescription, matchingByDescriptionUiElements, testSpecificData);
                } else {
                    bestMatchingElement = bestMatchingByDescriptionAndPageRelevanceUiElements.getFirst();
                }
            } else {
                bestMatchingElement = matchingByDescriptionUiElements.getFirst();
            }

            LOG.info("Found {} UI element(s) in DB corresponding to the description of '{}'. Element names: {}",
                    matchingByDescriptionUiElements.size(), elementDescription,
                    matchingByDescriptionUiElements.stream().map(UiElement::name).toList());
            return findElementAndProcessLocationResult(() -> getFinalElementLocation(bestMatchingElement, testSpecificData),
                    elementDescription, testSpecificData);
        }
    }

    @NotNull
    private static List<UiElement> getBestMatchingByDescriptionAndPageRelevanceUiElements(String elementDescription) {
        String pageDescription = getPageDescriptionFromModel();
        var retrievedWithPageRelevanceScoreElements = elementRetriever.retrieveUiElements(elementDescription,
                pageDescription, TOP_N_ELEMENTS_TO_RETRIEVE, MIN_GENERAL_RETRIEVAL_SCORE);
        return retrievedWithPageRelevanceScoreElements.stream()
                .filter(retrievedUiElementItem -> retrievedUiElementItem.pageRelevanceScore() >= MIN_PAGE_RELEVANCE_SCORE)
                .map(RetrievedUiElementItem::element)
                .toList();
    }

    private static Optional<Rectangle> processMultipleElementsRelevantToPageFoundCase(String elementDescription,
                                                                                      List<UiElement> bestMatchingByPageRelevanceUiElements,
                                                                                      List<RetrievedUiElementItem> retrievedElements,
                                                                                      String elementTestData) {
        if (UNATTENDED_MODE) {
            var message = ("Found not a single, but %d UI elements in DB which correspond to '%s' " +
                    "and all have the minimum required page relevance score. Please refine them using attended " +
                    "mode.").formatted(bestMatchingByPageRelevanceUiElements.size(), elementDescription);
            throw new IllegalStateException(message);
        } else {
            var reasonToRefine = ("I have found more than one UI element in my Database which have minimum page " +
                    "relevance score and match the description '%s'").formatted(elementDescription);
            promptUserToRefinePossibleCandidateUiElements(retrievedElements, reasonToRefine);
            return promptUserForNextAction(elementDescription, elementTestData);
        }
    }

    private static Optional<Rectangle> processNoElementsRelevantToPageFoundCase(String elementDescription,
                                                                                List<UiElement> originallyFoundUiElements,
                                                                                String elementTestData) {
        if (UNATTENDED_MODE) {
            var message = ("No matching elements by page relevance found, but there were %s " +
                    "UI elements matching the description '%s' initially. Please lower the page relevance threshold or refine them using " +
                    "attended mode.")
                    .formatted(originallyFoundUiElements.size(), elementDescription);
            throw new IllegalStateException(message);
        } else {
            var reasonToRefine = ("I have found no UI elements in my Database which have the minimum page " +
                    "relevance score and match the description '%s'. However, I have found %d UI elements matching this description " +
                    "without taking into account their page relevance. If the target element is in this list, you could update its " +
                    "description and anchors information to correspond better to the page/view where its located.")
                    .formatted(elementDescription, originallyFoundUiElements.size());
            promptUserToRefineUiElements(reasonToRefine, originallyFoundUiElements);
            return promptUserForNextAction(elementDescription, elementTestData);
        }
    }


    private static Optional<Rectangle> processNoElementsFoundInDbWithSimilarCandidatesPresentCase(String elementDescription,
                                                                                                  List<RetrievedUiElementItem> retrievedElements,
                                                                                                  String elementTestData) {
        if (UNATTENDED_MODE) {
            var retrievedElementsString = retrievedElements.stream()
                    .map(el -> "%s --> %.1f".formatted(el.element().name(), el.mainScore()))
                    .collect(joining(", "));
            LOG.warn("No UI elements found in vector DB which semantically match the description '{}' with the " +
                            "similarity mainScore > {}. The most similar element names by similarity mainScore are: {}", elementDescription,
                    "%.1f".formatted(MIN_TARGET_RETRIEVAL_SCORE), retrievedElementsString);
            return empty();
        } else {
            // This one happens as soon as DB has some elements, but none of them has the similarity higher than the configured threshold
            var reasonToRefine = "I haven't found any UI elements in my Database which perfectly match the description '%s'"
                    .formatted(elementDescription);
            promptUserToRefinePossibleCandidateUiElements(retrievedElements, reasonToRefine);
            return promptUserForNextAction(elementDescription, elementTestData);
        }
    }


    @NotNull
    private static Optional<Rectangle> processNoElementsFoundInDbCase(String elementDescription) {
        if (UNATTENDED_MODE) {
            LOG.warn("No UI elements found in vector DB which semantically match the description '{}' with the " +
                    "similarity mainScore > {}.", elementDescription, "%.1f".formatted(MIN_GENERAL_RETRIEVAL_SCORE));
            return empty();
        } else {
            // This one will be seldom, because after at least some elements are in DB, they will be displayed
            NewElementInfoNeededPopup.display(elementDescription);
            return of(promptUserForCreatingNewElement(elementDescription));
        }
    }

    private static String getPageDescriptionFromModel() {
        var pageDescriptionPrompt = PageDescriptionPrompt.builder()
                .withScreenshot(captureScreen())
                .build();
        try (var model = getVerificationVisionModel()) {
            var pageDescriptionResult = model.generateAndGetResponseAsObject(pageDescriptionPrompt,
                    "generating the description of the page relative to the element");
            return pageDescriptionResult.pageDescription();
        }
    }

    private static void promptUserToRefinePossibleCandidateUiElements(List<RetrievedUiElementItem> retrievedElements,
                                                                      String refinementReason) {
        List<UiElement> elementsToRefine = retrievedElements.stream()
                .map(RetrievedUiElementItem::element)
                .toList();
        var message = ("'%s'. You could update or delete the following ones in order to have " +
                "more adequate search results next time:").formatted(refinementReason);
        promptUserToRefineUiElements(message, elementsToRefine);
    }

    private static void promptUserToRefineUiElements(String message, List<UiElement> elementsToRefine) {
        Function<UiElement, UiElement> elementUpdater = element -> {
            var clarifiedByUserElement = UiElementInfoPopup.displayAndGetUpdatedElement(element)
                    .orElseThrow(UserInterruptedExecutionException::new);
            if (!element.equals(clarifiedByUserElement)) {
                try {
                    elementRetriever.updateElement(element, clarifiedByUserElement);
                } catch (Exception e) {
                    var logMessage = "Couldn't update the following UI element: " + element;
                    LOG.error(logMessage, e);
                    showMessageDialog(null, "Couldn't update the UI element, see the logs for details");
                }
            }

            return clarifiedByUserElement;
        };

        Consumer<UiElement> elementRemover = element -> {
            try {
                elementRetriever.removeElement(element);
            } catch (Exception e) {
                var logMessage = "Couldn't delete the following UI element: " + element;
                LOG.error(logMessage, e);
                showMessageDialog(null, "Couldn't delete the UI element, see the logs for details");
            }
        };

        UiElementRefinementPopup.display(message, elementsToRefine, elementUpdater, elementRemover);
    }

    private static Optional<Rectangle> findElementAndProcessLocationResult(Supplier<UiElementLocationResult> resultSupplier,
                                                                           String elementDescription, String elementTestData) {
        var locationResult = resultSupplier.get();
        return switch (locationResult) {
            case UiElementLocationResult(boolean patternMatch, var _, var _, var elementUsed) when !patternMatch ->
                    processNoPatternMatchesCase(elementDescription, elementUsed, elementTestData);
            case UiElementLocationResult(var _, boolean visualMatchByModel, var _, var elementUsed) when
                    !visualMatchByModel -> processNoVisualMatchCase(elementDescription, elementUsed, elementTestData);
            case UiElementLocationResult(var _, var _, Rectangle boundingBox, _) when boundingBox != null -> {
                LOG.info("The best visual match for the description '{}' has been located at: {}", elementDescription, boundingBox);
                yield of(getScaledBoundingBox(boundingBox));
            }
            default -> throw new IllegalStateException("Got element location result in unexpected state: " + locationResult);
        };
    }

    private static Optional<Rectangle> processNoVisualMatchCase(String elementDescription, UiElement elementUsed, String elementTestData) {
        var rootCause = ("Visual pattern matching provided results, but the model has decided that none of them visually " +
                "matches the description '%s'. Either this is a bug, or the UI has been modified and the saved in DB UI element " +
                "info is obsolete. Do you wish to refine the UI element info or to terminate the execution ?")
                .formatted(elementDescription);
        if (UNATTENDED_MODE) {
            LOG.warn(rootCause);
            return empty();
        } else {
            return processNoElementFoundCaseInAttendedMode(elementDescription, elementUsed, rootCause, elementTestData);
        }
    }

    private static Optional<Rectangle> processNoPatternMatchesCase(String elementDescription, UiElement elementUsed,
                                                                   String elementTestData) {
        var rootCause = ("Visual pattern matching provided no results within deadline. Either this is a bug, or most probably " +
                "the UI has been modified and the saved in DB UI element info is obsolete. The element description is: '%s'. Do " +
                "you wish to refine the UI element info or to terminate the execution ?").formatted(elementDescription);
        if (UNATTENDED_MODE) {
            LOG.warn(rootCause);
            return empty();
        } else {
            return processNoElementFoundCaseInAttendedMode(elementDescription, elementUsed, rootCause, elementTestData);
        }
    }

    private static Optional<Rectangle> processNoElementFoundCaseInAttendedMode(String elementDescription, @NotNull UiElement elementUsed,
                                                                               String rootCause, String elementTestData) {
        return switch (NoElementFoundPopup.displayAndGetUserDecision(rootCause)) {
            case CONTINUE -> {
                var message = "You could update or delete the element which was used in the search in order to have " +
                        "more adequate search results next time:";
                promptUserToRefineUiElements(message, List.of(elementUsed));
                yield promptUserForNextAction(elementDescription, elementTestData);
            }
            case TERMINATE -> {
                logUserTerminationRequest();
                throw new UserChoseTerminationException();
            }
        };
    }

    private static Optional<Rectangle> promptUserForNextAction(String elementDescription, String elementTestData) {
        return switch (NextActionPopup.displayAndGetUserDecision()) {
            case RETRY_SEARCH -> locateElementOnTheScreen(elementDescription, elementTestData);
            case CREATE_NEW_ELEMENT -> {
                sleepMillis(USER_DIALOG_DISMISS_DELAY_MILLIS);
                yield of(promptUserForCreatingNewElement(elementDescription));
            }
            case TERMINATE -> {
                logUserTerminationRequest();
                throw new UserChoseTerminationException();
            }
        };
    }

    private static void logUserTerminationRequest() {
        LOG.warn("The user decided to terminate the execution. Exiting...");
    }

    private static UiElementLocationResult getFinalElementLocation(UiElement elementRetrievedFromMemory, String elementTestData) {
        var elementScreenshot = elementRetrievedFromMemory.screenshot().toBufferedImage();
        BufferedImage wholeScreenshot = captureScreen();
        if (elementRetrievedFromMemory.zoomInRequired()) {
            LOG.info("Zoom-in is needed for element '{}'. Performing initial wide-area search.", elementRetrievedFromMemory.name());
            List<Rectangle> initialCandidates =
                    identifyBoundingBoxesUsingVision(elementRetrievedFromMemory, wholeScreenshot, elementTestData);
            if (initialCandidates.isEmpty()) {
                return new UiElementLocationResult(false, false, null, elementRetrievedFromMemory);
            }

            var zoomInOriginalRegion = getCommonArea(initialCandidates);
            var zoomInExtendedRegion = extendZoomInRegion(zoomInOriginalRegion, elementScreenshot, wholeScreenshot);
            var zoomInImage = cloneImage(wholeScreenshot.getSubimage(zoomInExtendedRegion.x, zoomInExtendedRegion.y,
                    zoomInExtendedRegion.width, zoomInExtendedRegion.height));
            var zoomedInScreenshot = getScaledUpImage(zoomInImage, ZOOM_SCALE_FACTOR);
            var elementLocationResult = getUiElementLocationResult(elementRetrievedFromMemory, elementTestData,
                    zoomedInScreenshot, elementScreenshot, false);
            if (elementLocationResult.boundingBox() != null) {
                var rescaledBoundingBox = getOriginalBox(elementLocationResult.boundingBox());
                var actualX = zoomInExtendedRegion.x + rescaledBoundingBox.x;
                var actualY = zoomInExtendedRegion.y + rescaledBoundingBox.y;
                var actualBox = new Rectangle(actualX, actualY, rescaledBoundingBox.width, rescaledBoundingBox.height);
                return new UiElementLocationResult(elementLocationResult.patternMatchFound(), elementLocationResult.visualMatchFound(),
                        actualBox, elementLocationResult.elementUsedForLocation());
            } else {
                return elementLocationResult;
            }
        } else {
            return getUiElementLocationResult(elementRetrievedFromMemory, elementTestData, wholeScreenshot, elementScreenshot, true);
        }
    }

    @NotNull
    private static Rectangle getOriginalBox(Rectangle scaledBox) {
        int rescaledX = (int) (scaledBox.x / (double) ZOOM_SCALE_FACTOR);
        int rescaledY = (int) (scaledBox.y / (double) ZOOM_SCALE_FACTOR);
        int rescaledWidth = (int) (scaledBox.width / (double) ZOOM_SCALE_FACTOR);
        int rescaledHeight = (int) (scaledBox.height / (double) ZOOM_SCALE_FACTOR);
        return new Rectangle(rescaledX, rescaledY, rescaledWidth, rescaledHeight);
    }

    @NotNull
    private static Rectangle extendZoomInRegion(Rectangle zoomInOriginalRegion, BufferedImage elementScreenshot,
                                                BufferedImage wholeScreenshot) {
        var extensionRatio =
                zoomInOriginalRegion.width / ((double) elementScreenshot.getWidth() * ZOOM_IN_EXTENSION_RATIO_PROPORTIONAL_TO_ELEMENT);
        if (extensionRatio >= 1.0) {
            int newWidth = (int) (zoomInOriginalRegion.width * extensionRatio);
            int newHeight = (int) (zoomInOriginalRegion.height * extensionRatio);
            int newLeftX = Math.max(0, zoomInOriginalRegion.x - (newWidth - zoomInOriginalRegion.width) / 2);
            int newTopY = Math.max(0, zoomInOriginalRegion.y - (newHeight - zoomInOriginalRegion.height) / 2);
            int newRightX = Math.min(wholeScreenshot.getWidth() - 1, newLeftX + newWidth);
            int newBottomY = Math.min(wholeScreenshot.getHeight() - 1, newTopY + newHeight);
            zoomInOriginalRegion = new Rectangle(newLeftX, newTopY, newRightX - newLeftX, newBottomY - newTopY);
        }
        return zoomInOriginalRegion;
    }

    private static UiElementLocationResult getUiElementLocationResult(UiElement elementRetrievedFromMemory, String elementTestData,
                                                                      BufferedImage wholeScreenshot, BufferedImage elementScreenshot,
                                                                      boolean useAlgorithmicSearch) {
        var identifiedByVisionBoundingBoxes =
                identifyBoundingBoxesUsingVision(elementRetrievedFromMemory, wholeScreenshot, elementTestData);
        var gridBasedBoundingBoxes = identifyBoundingBoxesWithGridOverlay(elementRetrievedFromMemory, wholeScreenshot);
        List<Rectangle> featureMatchedBoundingBoxes = new LinkedList<>();
        List<Rectangle> templateMatchedBoundingBoxes = new LinkedList<>();
        if (useAlgorithmicSearch) {
            var featureMatchedBoundingBoxesByElementFuture = supplyAsync(() ->
                    findMatchingRegionsWithORB(wholeScreenshot, elementScreenshot));
            var templateMatchedBoundingBoxesByElementFuture = supplyAsync(() ->
                    mergeOverlappingRectangles(findMatchingRegionsWithTemplateMatching(wholeScreenshot, elementScreenshot)));
            featureMatchedBoundingBoxes = featureMatchedBoundingBoxesByElementFuture.join();
            templateMatchedBoundingBoxes = templateMatchedBoundingBoxesByElementFuture.join();
            if (DEBUG_MODE) {
                markElementsToPlotWithBoundingBoxes(cloneImage(wholeScreenshot),
                        getElementToPlot(elementRetrievedFromMemory, featureMatchedBoundingBoxes), "opencv_features_original");
                markElementsToPlotWithBoundingBoxes(cloneImage(wholeScreenshot),
                        getElementToPlot(elementRetrievedFromMemory, templateMatchedBoundingBoxes), "opencv_template_original");
            }
        }

        if (DEBUG_MODE) {
            var image = cloneImage(wholeScreenshot);
            identifiedByVisionBoundingBoxes.forEach(box -> drawBoundingBox(image, box, BOUNDING_BOX_COLOR));
            saveImage(image, "vision_original");
        }

        return getUiElementLocationResult(elementRetrievedFromMemory, wholeScreenshot, identifiedByVisionBoundingBoxes,
                featureMatchedBoundingBoxes, templateMatchedBoundingBoxes, gridBasedBoundingBoxes);
    }

    private static UiElementLocationResult getUiElementLocationResult(UiElement elementRetrievedFromMemory, BufferedImage wholeScreenshot,
                                                                      List<Rectangle> identifiedByVisionBoundingBoxes,
                                                                      List<Rectangle> featureMatchedBoundingBoxes,
                                                                      List<Rectangle> templateMatchedBoundingBoxes,
                                                                      List<Rectangle> gridBasedBoundingBoxes) {
        if (identifiedByVisionBoundingBoxes.isEmpty() && featureMatchedBoundingBoxes.isEmpty() &&
                templateMatchedBoundingBoxes.isEmpty() && gridBasedBoundingBoxes.isEmpty()) {
            return new UiElementLocationResult(false, false, null, elementRetrievedFromMemory);
        } else if (identifiedByVisionBoundingBoxes.isEmpty()) {
            LOG.info("Vision model provided no detection results, proceeding with algorithmic matches");
            return chooseBestAlgorithmicMatch(elementRetrievedFromMemory, wholeScreenshot, featureMatchedBoundingBoxes,
                    templateMatchedBoundingBoxes);
        } else {
            if (featureMatchedBoundingBoxes.isEmpty() && templateMatchedBoundingBoxes.isEmpty()) {
                return selectBestMatchingUiElementUsingModel(elementRetrievedFromMemory, identifiedByVisionBoundingBoxes, wholeScreenshot,
                        "vision_only");
            } else {
                return chooseBestCommonMatch(elementRetrievedFromMemory, identifiedByVisionBoundingBoxes, wholeScreenshot,
                        featureMatchedBoundingBoxes, templateMatchedBoundingBoxes)
                        .orElseGet(() -> {
                            var algorithmicIntersections = getIntersections(featureMatchedBoundingBoxes,
                                    templateMatchedBoundingBoxes);
                            if (!algorithmicIntersections.isEmpty()) {
                                var boxes = concat(identifiedByVisionBoundingBoxes.stream(), algorithmicIntersections.stream()).toList();
                                return selectBestMatchingUiElementUsingModel(elementRetrievedFromMemory, boxes, wholeScreenshot,
                                        "vision_and_algorithmic_only_intersections");
                            } else {
                                var boxes = Stream.of(identifiedByVisionBoundingBoxes, featureMatchedBoundingBoxes,
                                                templateMatchedBoundingBoxes)
                                        .flatMap(Collection::stream)
                                        .toList();
                                return selectBestMatchingUiElementUsingModel(elementRetrievedFromMemory, boxes, wholeScreenshot,
                                        "vision_and_algorithmic_regions_separately");
                            }
                        });
            }
        }
    }

    @NotNull
    private static Optional<UiElementLocationResult> chooseBestCommonMatch(UiElement matchingUiElement,
                                                                           List<Rectangle> identifiedByVisionBoundingBoxes,
                                                                           BufferedImage wholeScreenshot, List<Rectangle> featureRects,
                                                                           List<Rectangle> templateRects) {
        LOG.info("Mapping provided by vision model results to the algorithmic ones");
        var visionAndFeatureIntersections = getIntersections(identifiedByVisionBoundingBoxes, featureRects);
        var visionAndTemplateIntersections = getIntersections(identifiedByVisionBoundingBoxes, templateRects);
        var bestIntersections = getIntersections(visionAndFeatureIntersections, visionAndTemplateIntersections);

        if (!bestIntersections.isEmpty()) {
            if (bestIntersections.size() > 1) {
                LOG.info("Found {} common vision model and algorithmic regions, using them for further refinement by " +
                        "the model.", bestIntersections.size());
                return of(selectBestMatchingUiElementUsingModel(matchingUiElement, bestIntersections, wholeScreenshot, "intersection_all"));
            } else {
                LOG.info("Found a single common vision model and common algorithmic region, returning it");
                return of(new UiElementLocationResult(true, true, bestIntersections.getFirst(), matchingUiElement));
            }
        } else {
            var goodIntersections = Stream.of(visionAndFeatureIntersections.stream(), visionAndTemplateIntersections.stream())
                    .flatMap(Stream::distinct)
                    .toList();
            if (!goodIntersections.isEmpty()) {
                LOG.info("Found {} common regions between vision model and either template or feature matching algorithms, " +
                        "using them for further refinement by the model.", goodIntersections.size());
                return of(selectBestMatchingUiElementUsingModel(matchingUiElement, goodIntersections, wholeScreenshot,
                        "intersection_vision_and_one_algorithm"));
            } else {
                LOG.info("Found no common regions between vision model and either template or feature matching algorithms");
                return empty();
            }
        }
    }

    private static UiElementLocationResult chooseBestAlgorithmicMatch(UiElement matchingUiElement, BufferedImage wholeScreenshot,
                                                                      List<Rectangle> featureMatchedBoxes,
                                                                      List<Rectangle> templateMatchedBoxes) {
        if (templateMatchedBoxes.isEmpty() && featureMatchedBoxes.isEmpty()) {
            LOG.info("No algorithmic matches provided for selection");
            return new UiElementLocationResult(false, false, null, matchingUiElement);
        }

        var algorithmicIntersections = getIntersections(templateMatchedBoxes, featureMatchedBoxes);
        if (!algorithmicIntersections.isEmpty()) {
            LOG.info("Found {} common detection regions between algorithmic matches, using them for further refinement by the " +
                    "model.", algorithmicIntersections.size());
            return selectBestMatchingUiElementUsingModel(matchingUiElement, algorithmicIntersections, wholeScreenshot,
                    "intersection_feature_and_template");
        } else {
            LOG.info("Found no common detection regions between algorithmic matches, using all originally detected regions for " +
                    "further refinement by the model.");
            var combinedBoundingBoxes = concat(featureMatchedBoxes.stream(), templateMatchedBoxes.stream()).toList();
            return selectBestMatchingUiElementUsingModel(matchingUiElement, combinedBoundingBoxes, wholeScreenshot,
                    "all_feature_and_template");
        }
    }

    private static List<Rectangle> identifyBoundingBoxesUsingVision(UiElement element, BufferedImage wholeScreenshot,
                                                                    String elementTestData) {
        var startTime = Instant.now();
        LOG.info("Asking model to identify bounding boxes for each element which looks like '{}'.", element.name());
        try {
            var elementBoundingBoxPrompt = ElementBoundingBoxPrompt.builder()
                    .withUiElement(element)
                    .withScreenshot(wholeScreenshot)
                    .withElementTestData(elementTestData)
                    .build();

            try (var executor = newVirtualThreadPerTaskExecutor();
                 var model = getModel(getBboxIdentificationModelName(), getBboxIdentificationModelProvider())) {
                List<Callable<List<BoundingBox>>> tasks = range(0, VISUAL_GROUNDING_MODEL_VOTE_COUNT)
                        .mapToObj(i -> (Callable<List<BoundingBox>>) () -> model.generateAndGetResponseAsObject(elementBoundingBoxPrompt,
                                "getting bounding boxes from vision model (vote #" + i + ")").boundingBoxes())
                        .toList();
                List<Rectangle> allBoundingBoxes = executor.invokeAll(tasks).stream()
                        .map(future -> getFutureResult(future, "getting bounding boxes from vision model"))
                        .flatMap(Optional::stream)
                        .flatMap(Collection::stream)
                        .map(bb -> bb.getActualBoundingBox(wholeScreenshot.getWidth(), wholeScreenshot.getHeight()))
                        .toList();

                if (DEBUG_MODE) {
                    var imageWithAllBoxes = cloneImage(wholeScreenshot);
                    allBoundingBoxes.forEach(box -> drawBoundingBox(imageWithAllBoxes, box, BOUNDING_BOX_COLOR));
                    saveImage(imageWithAllBoxes, "vision_identified_boxes_before_clustering");
                }

                if (allBoundingBoxes.isEmpty()) {
                    return List.of();
                }

                if (VISUAL_GROUNDING_MODEL_VOTE_COUNT > 1) {
                    var minPointsForCluster = VISUAL_GROUNDING_MODEL_VOTE_COUNT > 2 ? 2 : 1;
                    DBSCANClusterer<RectangleAdapter> clusterer =
                            new DBSCANClusterer<>(BBOX_CLUSTERING_MIN_INTERSECTION_RATIO, minPointsForCluster, new IoUDistance());
                    List<RectangleAdapter> points = allBoundingBoxes.stream().map(RectangleAdapter::new).toList();
                    List<Cluster<RectangleAdapter>> clusters = clusterer.cluster(points);
                    var result = clusters.stream()
                            .map(cluster -> {
                                List<Rectangle> clusterBoxes = cluster.getPoints().stream().map(RectangleAdapter::getRectangle).toList();
                                return calculateAverageBoundingBox(clusterBoxes);
                            })
                            .toList();
                    LOG.info("Model identified {} bounding boxes with {} votes, resulting in {} common regions",
                            allBoundingBoxes.size(), VISUAL_GROUNDING_MODEL_VOTE_COUNT, result.size());
                    return result;
                } else {
                    LOG.info("Model identified {} bounding boxes", allBoundingBoxes.size());
                    return allBoundingBoxes;
                }
            } catch (InterruptedException e) {
                currentThread().interrupt();
                LOG.error("Got interrupted while collecting bounding boxes from the model", e);
                return List.of();
            }
        } finally {
            LOG.info("Finished identifying bounding boxes using vision in {} ms", Duration.between(startTime, Instant.now()).toMillis());
        }
    }

    private static List<Rectangle> identifyBoundingBoxesWithGridOverlay(UiElement element, BufferedImage wholeScreenshot) {
        var startTime = Instant.now();
        LOG.info("Asking model to identify bounding boxes using grid overlay for element '{}'.", element.name());
        try {
            BufferedImage gridImage = drawGrid(cloneImage(wholeScreenshot), GRID_ROWS, GRID_COLS);
            var prompt = GridOverlayBoundingBoxPrompt.builder()
                    .withUiElement(element)
                    .withScreenshot(gridImage)
                    .build();
            try (var model = getModel(AgentConfig.getGridOverlayModelName(), AgentConfig.getGridOverlayModelProvider())) {
                BoundingBox box = model.generateAndGetResponseAsObject(prompt, "getting bounding box from grid");
                return List.of(box.getActualBoundingBox(wholeScreenshot.getWidth(), wholeScreenshot.getHeight()));
            }
        } finally {
            LOG.info("Finished identifying bounding boxes using grid overlay in {} ms",
                    Duration.between(startTime, Instant.now()).toMillis());
        }
    }

    private static Rectangle calculateAverageBoundingBox(List<Rectangle> boxes) {
        if (boxes.isEmpty()) {
            return new Rectangle();
        }
        int x = (int) boxes.stream().mapToDouble(Rectangle::getX).average().orElse(0);
        int y = (int) boxes.stream().mapToDouble(Rectangle::getY).average().orElse(0);
        int width = (int) boxes.stream().mapToDouble(Rectangle::getWidth).average().orElse(0);
        int height = (int) boxes.stream().mapToDouble(Rectangle::getHeight).average().orElse(0);
        return new Rectangle(x, y, width, height);
    }

    @NotNull
    private static List<Rectangle> getIntersections(List<Rectangle> firstSet, List<Rectangle> secondSet) {
        return firstSet.stream()
                .flatMap(r1 -> secondSet.stream()
                        .map(r1::intersection)
                        .filter(intersection -> !intersection.isEmpty()))
                .toList();
    }

    private static Rectangle promptUserForCreatingNewElement(String originalElementDescription) {
        BoundingBoxCaptureNeededPopup.display();
        sleepMillis(USER_DIALOG_DISMISS_DELAY_MILLIS);
        var elementCaptureResult = UiElementScreenshotCaptureWindow.displayAndGetResult(BOUNDING_BOX_COLOR)
                .orElseThrow(UserInterruptedExecutionException::new);
        if (!elementCaptureResult.success()) {
            throw new IllegalStateException("Couldn't capture UI element bounding box. Please see logs for details");
        }
        var prompt = ElementDescriptionPrompt.builder()
                .withOriginalElementDescription(originalElementDescription)
                .withScreenshot(elementCaptureResult.wholeScreenshotWithBoundingBox())
                .withBoundingBoxColor(BOUNDING_BOX_COLOR)
                .build();

        try (var model = getVerificationVisionModel()) {
            var uiElementDescriptionResult = model.generateAndGetResponseAsObject(prompt,
                    "generating the description of selected UI element");
            var describedUiElement = new UiElement(randomUUID(), uiElementDescriptionResult.name(),
                    uiElementDescriptionResult.ownDescription(), uiElementDescriptionResult.anchorsDescription(),
                    uiElementDescriptionResult.pageSummary(), null, false, List.of());
            var clarifiedByUserElement = UiElementInfoPopup.displayAndGetUpdatedElement(describedUiElement)
                    .orElseThrow(UserInterruptedExecutionException::new);
            var elementBoundingBox = elementCaptureResult.boundingBox();
            initializeAndSaveNewUiElementIntoDb(elementCaptureResult.elementScreenshot(), clarifiedByUserElement);
            TargetElementToGetFocusPopup.display();
            sleepMillis(USER_DIALOG_DISMISS_DELAY_MILLIS);
            return getScaledBoundingBox(elementBoundingBox);
        }
    }

    private static void initializeAndSaveNewUiElementIntoDb(BufferedImage elementScreenshot, UiElement uiElement) {
        Screenshot screenshot = fromBufferedImage(elementScreenshot, "png");
        UiElement uiElementToStore = new UiElement(randomUUID(), uiElement.name(), uiElement.ownDescription(),
                uiElement.anchorsDescription(), uiElement.pageSummary(), screenshot, uiElement.zoomInRequired(),
                uiElement.dataDependentAttributes());
        elementRetriever.storeElement(uiElementToStore);
    }

    private static UiElementLocationResult selectBestMatchingUiElementUsingModel(UiElement uiElement,
                                                                                 List<Rectangle> matchedBoundingBoxes,
                                                                                 BufferedImage screenshot, String matchAlgorithm) {
        var startTime = Instant.now();
        LOG.info("Selecting the best visual match for UI element '{}'", uiElement.name());
        try {
            var boxedAmount = matchedBoundingBoxes.size();
            checkArgument(boxedAmount > 0, "Amount of bounding boxes to plot must be > 0");

            Map<String, Rectangle> boxesWithIds = getBoxesWithIds(matchedBoundingBoxes);

            var resultingScreenshot = cloneImage(screenshot);
            drawBoundingBoxes(resultingScreenshot, boxesWithIds);
            if (DEBUG_MODE) {
                saveImage(resultingScreenshot, "model_selection_%s".formatted(matchAlgorithm));
            }

            var successfulIdentificationResults =
                    getValidSuccessfulIdentificationResultsFromModelUsingQuorum(uiElement, resultingScreenshot,
                            new ArrayList<>(boxesWithIds.keySet()));
            LOG.info("Model provided {} successful identification results for the element '{}' with {} vote(s).",
                    successfulIdentificationResults.size(), uiElement.name(), VALIDATION_MODEL_VOTE_COUNT);
            var votesById = successfulIdentificationResults.stream()
                    .collect(groupingBy(r -> r.boundingBoxId().toLowerCase(), counting()));
            var maxVotes = max(votesById.values());
            var winners = votesById.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(maxVotes))
                    .map(Map.Entry::getKey)
                    .toList();
            if (winners.size() > 1) {
                LOG.warn("Found multiple winners with {} votes for element '{}': {}. Selecting the one with the largest bounding box area.",
                        maxVotes, uiElement.name(), winners);
                return winners.stream()
                        .map(boxesWithIds::get)
                        .max(comparingDouble(box -> box.getWidth() * box.getHeight()))
                        .map(box -> new UiElementLocationResult(true, true, box, uiElement))
                        .orElseGet(() -> new UiElementLocationResult(true, false, null, uiElement));
            } else {
                return new UiElementLocationResult(true, true, boxesWithIds.get(winners.getFirst()), uiElement);
            }
        } finally {
            LOG.info("Finished selecting best matching UI element using model in {} ms",
                    Duration.between(startTime, Instant.now()).toMillis());
        }
    }

    @NotNull
    private static List<UiElementIdentificationResult> getValidSuccessfulIdentificationResultsFromModelUsingQuorum(
            @NotNull UiElement uiElement,
            @NotNull BufferedImage resultingScreenshot,
            @NotNull List<String> boxIds) {
        try (var executor = newVirtualThreadPerTaskExecutor();
             var model = getVerificationVisionModel()) {
            var prompt = SelectBestUiElementPrompt.builder()
                    .withUiElement(uiElement)
                    .withScreenshot(resultingScreenshot)
                    .withBoundingBoxIds(boxIds)
                    .build();
            List<Callable<UiElementIdentificationResult>> tasks = range(0, VALIDATION_MODEL_VOTE_COUNT)
                    .mapToObj(i -> (Callable<UiElementIdentificationResult>) () -> model.generateAndGetResponseAsObject(prompt,
                            "identifying the best matching UI element (vote #%d)".formatted(i)))
                    .toList();
            return executor.invokeAll(tasks).stream()
                    .map(future -> getFutureResult(future, "UI element identification by the model"))
                    .flatMap(Optional::stream)
                    .filter(r -> r.success() && boxIds.contains(r.boundingBoxId()))
                    .toList();
        } catch (InterruptedException e) {
            currentThread().interrupt();
            LOG.error("Got interrupted while collecting UI element identification results by the model", e);
            return List.of();
        }
    }

    private static Map<String, Rectangle> getBoxesWithIds(List<Rectangle> boundingBoxes) {
        Map<String, Rectangle> boxesWithIds = new LinkedHashMap<>();
        for (Rectangle box : boundingBoxes) {
            String id;
            do {
                id = randomUUID().toString().substring(0, 4);
            } while (boxesWithIds.containsKey(id));
            boxesWithIds.put(id, box);
        }
        return boxesWithIds;
    }

    @NotNull
    private static PlottedUiElement getElementToPlot(UiElement element, List<Rectangle> matchedBoundingBoxes) {
        return new PlottedUiElement(element.name(), element, getBoxesWithIds(matchedBoundingBoxes));
    }

    private static void markElementsToPlotWithBoundingBoxes(BufferedImage resultingScreenshot, PlottedUiElement elementToPlot,
                                                            String postfix) {
        var elementBoundingBoxesByLabel = elementToPlot.boundingBoxesByIds();
        drawBoundingBoxes(resultingScreenshot, elementBoundingBoxesByLabel);
        if (DEBUG_MODE) {
            saveImage(resultingScreenshot, postfix);
        }
    }

    private record PlottedUiElement(String id, UiElement uiElement, Map<String, Rectangle> boundingBoxesByIds) {
    }

    private record UiElementLocationResult(boolean patternMatchFound, boolean visualMatchFound, Rectangle boundingBox,
                                           UiElement elementUsedForLocation) {
    }

    private static class RectangleAdapter implements Clusterable {
        private final Rectangle rectangle;
        private final double[] points;

        public RectangleAdapter(Rectangle rectangle) {
            this.rectangle = rectangle;
            this.points = new double[]{rectangle.x, rectangle.y, rectangle.width, rectangle.height};
        }

        public Rectangle getRectangle() {
            return rectangle;
        }

        @Override
        public double[] getPoint() {
            return points;
        }
    }

    private static class IoUDistance implements DistanceMeasure {
        @Override
        public double compute(double[] a, double[] b) {
            Rectangle r1 = new Rectangle((int) a[0], (int) a[1], (int) a[2], (int) a[3]);
            Rectangle r2 = new Rectangle((int) b[0], (int) b[1], (int) b[2], (int) b[3]);
            return 1 - calculateIoU(r1, r2);
        }
    }
}