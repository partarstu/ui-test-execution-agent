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

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.Clusterable;
import org.apache.commons.math3.ml.clustering.DBSCANClusterer;
import org.apache.commons.math3.ml.distance.DistanceMeasure;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.AgentConfig;
import org.tarik.ta.agents.ElementBoundingBoxAgent;
import org.tarik.ta.agents.ElementSelectionAgent;
import org.tarik.ta.agents.PageDescriptionAgent;
import org.tarik.ta.agents.UiStateCheckAgent;
import org.tarik.ta.dto.BoundingBox;
import org.tarik.ta.dto.ElementLocation;
import org.tarik.ta.dto.UiElementIdentificationResult;
import org.tarik.ta.exceptions.ElementLocationException;
import org.tarik.ta.exceptions.ElementLocationException.ElementLocationStatus;
import org.tarik.ta.exceptions.ToolExecutionException;
import org.tarik.ta.rag.RetrieverFactory;
import org.tarik.ta.rag.UiElementRetriever;
import org.tarik.ta.rag.UiElementRetriever.RetrievedUiElementItem;
import org.tarik.ta.rag.model.UiElement;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.List;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static dev.langchain4j.service.AiServices.builder;
import static java.lang.Math.min;
import static java.lang.Thread.currentThread;
import static java.time.Duration.between;
import static java.util.Collections.max;
import static java.util.Comparator.comparingDouble;
import static java.util.Optional.*;
import static java.util.UUID.randomUUID;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;
import static java.util.stream.Collectors.*;
import static java.util.stream.IntStream.range;
import static java.util.stream.Stream.concat;
import static org.tarik.ta.AgentConfig.getGuiGroundingModelName;
import static org.tarik.ta.AgentConfig.getGuiGroundingModelProvider;
import static org.tarik.ta.error.ErrorCategory.*;
import static org.tarik.ta.model.ModelFactory.getModel;
import static org.tarik.ta.utils.BoundingBoxUtil.*;
import static org.tarik.ta.utils.CommonUtils.*;
import static org.tarik.ta.utils.ImageMatchingUtil.findMatchingRegionsWithORB;
import static org.tarik.ta.utils.ImageMatchingUtil.findMatchingRegionsWithTemplateMatching;
import static org.tarik.ta.utils.ImageUtils.*;
import static org.tarik.ta.utils.PromptUtils.singleImageContent;

public class ElementLocatorTools extends AbstractTools {
    private static final Logger LOG = LoggerFactory.getLogger(ElementLocatorTools.class);
    private static final double MIN_TARGET_RETRIEVAL_SCORE = AgentConfig.getElementRetrievalMinTargetScore();
    private static final double MIN_PAGE_RELEVANCE_SCORE = AgentConfig.getElementRetrievalMinPageRelevanceScore();
    private static final double MIN_GENERAL_RETRIEVAL_SCORE = AgentConfig.getElementRetrievalMinGeneralScore();
    private static final String BOUNDING_BOX_COLOR_NAME = AgentConfig.getElementBoundingBoxColorName();
    private static final Color BOUNDING_BOX_COLOR = getColorByName(BOUNDING_BOX_COLOR_NAME);
    private static final int TOP_N_ELEMENTS_TO_RETRIEVE = AgentConfig.getRetrieverTopN();
    private final UiElementRetriever elementRetriever;
    private static final boolean DEBUG_MODE = AgentConfig.isDebugMode();

    private final PageDescriptionAgent pageDescriptionAgent;
    private final ElementBoundingBoxAgent elementBoundingBoxAgent;
    private final ElementSelectionAgent elementSelectionAgent;

    public ElementLocatorTools() {
        super();
        this.elementRetriever = RetrieverFactory.getUiElementRetriever();
        this.pageDescriptionAgent = createPageDescriptionAgent();
        this.elementBoundingBoxAgent = createElementBoundingBoxAgent();
        this.elementSelectionAgent = createElementSelectionAgent();
    }

    public ElementLocatorTools(UiStateCheckAgent uiStateCheckAgent) {
        super(uiStateCheckAgent);
        this.elementRetriever = RetrieverFactory.getUiElementRetriever();
        this.pageDescriptionAgent = createPageDescriptionAgent();
        this.elementBoundingBoxAgent = createElementBoundingBoxAgent();
        this.elementSelectionAgent = createElementSelectionAgent();
    }

    private PageDescriptionAgent createPageDescriptionAgent() {
        var model = getModel(AgentConfig.getVerificationVisionModelName(), AgentConfig.getVerificationVisionModelProvider());
        return builder(PageDescriptionAgent.class)
                .chatModel(model.getChatModel())
                .build();
    }

    private ElementBoundingBoxAgent createElementBoundingBoxAgent() {
        var model = getModel(AgentConfig.getGuiGroundingModelName(), AgentConfig.getGuiGroundingModelProvider());
        return builder(ElementBoundingBoxAgent.class)
                .chatModel(model.getChatModel())
                .build();
    }

    private ElementSelectionAgent createElementSelectionAgent() {
        var model = getModel(AgentConfig.getVerificationVisionModelName(), AgentConfig.getVerificationVisionModelProvider());
        return builder(ElementSelectionAgent.class)
                .chatModel(model.getChatModel())
                .build();
    }

    private String formatElementBoundingBoxPrompt(UiElement uiElement, String elementTestData) {
        if (isNotBlank(elementTestData) && !uiElement.dataDependentAttributes().isEmpty()) {
            return """
                    The target element:
                    "%s. %s %s"
                    
                    This element is data-dependent.
                    The element attributes which depend on specific data: [%s].
                    Available specific data for this element: "%s"
                    """.formatted(uiElement.name(), uiElement.description(), uiElement.locationDetails(),
                    String.join(", ", uiElement.dataDependentAttributes()), elementTestData);
        } else {
            return """
                    The target element: "%s. %s %s"
                    """.formatted(uiElement.name(), uiElement.description(), uiElement.locationDetails());
        }
    }

    private String formatElementSelectionPrompt(UiElement uiElement, String elementTestData, List<String> boundingBoxIds) {
        String boundingBoxIdsString = "Bounding box IDs: %s.".formatted(String.join(", ", boundingBoxIds));
        if (isNotBlank(elementTestData) && !uiElement.dataDependentAttributes().isEmpty()) {
            return """
                    The target element:
                    "%s. %s %s"
                    
                    This element is data-dependent.
                    The element attributes which depend on specific data: [%s].
                    Available specific data for this element: "%s"
                    
                    %s
                    """.formatted(uiElement.name(), uiElement.description(), uiElement.locationDetails(),
                    String.join(", ", uiElement.dataDependentAttributes()), elementTestData, boundingBoxIdsString);
        } else {
            return """
                    The target element: "%s. %s %s"
                    
                    %s
                    """.formatted(uiElement.name(), uiElement.description(), uiElement.locationDetails(),
                    boundingBoxIdsString);
        }
    }

    private static final int VISUAL_GROUNDING_MODEL_VOTE_COUNT = AgentConfig.getElementLocatorVisualGroundingVoteCount();
    private static final int VALIDATION_MODEL_VOTE_COUNT = AgentConfig.getElementLocatorValidationVoteCount();
    private static final double BBOX_CLUSTERING_MIN_INTERSECTION_RATIO = AgentConfig.getBboxClusteringMinIntersectionRatio();
    private static final double ZOOM_IN_EXTENSION_RATIO_PROPORTIONAL_TO_ELEMENT = 15.0;
    private static final int BBOX_SCREENSHOT_LONGEST_ALLOWED_DIMENSION_PIXELS =
            AgentConfig.getBboxScreenshotLongestAllowedDimensionPixels();
    private static final double BBOX_SCREENSHOT_MAX_SIZE_MEGAPIXELS = AgentConfig.getBboxScreenshotMaxSizeMegapixels();

    @Tool(value = "Locates the UI element on the screen based on its description and returns its coordinates.")
    public ElementLocation locateElementOnTheScreen(
            @P("A detailed description of the UI element to locate (e.g., 'Submit button', 'Username input field', " +
                    "'Cancel link in the dialog')")
            String elementDescription,
            @P(value = "All available data related to this element (e.g., text content, identifiers, input data etc.).", required = false)
            String testSpecificData) {
        if (isBlank(elementDescription)) {
            throw new ToolExecutionException("Element description cannot be empty", TRANSIENT_TOOL_ERROR);
        }
        try {
            var retrievedElements = elementRetriever.retrieveUiElements(elementDescription, TOP_N_ELEMENTS_TO_RETRIEVE,
                    MIN_GENERAL_RETRIEVAL_SCORE);
            var matchingByDescriptionUiElements = retrievedElements.stream()
                    .filter(retrievedUiElementItem -> retrievedUiElementItem
                            .mainScore() >= MIN_TARGET_RETRIEVAL_SCORE)
                    .sorted(comparingDouble(RetrievedUiElementItem::mainScore).reversed())
                    .map(RetrievedUiElementItem::element)
                    .toList();
            if (matchingByDescriptionUiElements.isEmpty() && !retrievedElements.isEmpty()) {
                throw processNoElementsFoundInDbWithSimilarCandidatesPresentCase(elementDescription, retrievedElements);
            } else if (matchingByDescriptionUiElements.isEmpty()) {
                throw processNoElementsFoundInDbCase(elementDescription);
            } else {
                UiElement bestMatchingElement;
                if (matchingByDescriptionUiElements.size() > 1) {
                    LOG.info("{} UI elements found in vector DB which semantically match the description '{}'. Scoring them based on " +
                            "the relevance to the currently opened page.", matchingByDescriptionUiElements.size(), elementDescription);
                    var bestMatchingByDescriptionAndPageRelevanceUiElements = getBestMatchingByDescriptionAndPageRelevanceUiElements(
                            elementDescription);
                    bestMatchingElement = bestMatchingByDescriptionAndPageRelevanceUiElements.getFirst();
                } else {
                    bestMatchingElement = matchingByDescriptionUiElements.getFirst();
                }
                LOG.info("Found {} UI element(s) in DB corresponding to the description of '{}'. Element names: {}",
                        matchingByDescriptionUiElements.size(), elementDescription,
                        matchingByDescriptionUiElements.stream().map(UiElement::name).toList());
                return findElementAndProcessLocationResult(() ->
                        getFinalElementLocation(bestMatchingElement, testSpecificData), elementDescription);
            }
        } catch (Exception e) {
            throw rethrowAsToolException(e, "locating a UI element on the screen");
        }
    }

    @NotNull
    private List<UiElement> getBestMatchingByDescriptionAndPageRelevanceUiElements(String elementDescription) {
        String pageDescription = getPageDescriptionFromModel();
        var retrievedWithPageRelevanceScoreElements = elementRetriever.retrieveUiElements(elementDescription,
                pageDescription, TOP_N_ELEMENTS_TO_RETRIEVE, MIN_GENERAL_RETRIEVAL_SCORE);
        return retrievedWithPageRelevanceScoreElements.stream()
                .filter(retrievedUiElementItem -> retrievedUiElementItem
                        .pageRelevanceScore() >= MIN_PAGE_RELEVANCE_SCORE)
                .map(RetrievedUiElementItem::element)
                .toList();
    }

    private ElementLocationException processNoElementsFoundInDbWithSimilarCandidatesPresentCase(
            String elementDescription, List<RetrievedUiElementItem> retrievedElements) {
        var retrievedElementsString = retrievedElements.stream()
                .map(el -> "%s --> %.1f".formatted(el.element().name(), el.mainScore()))
                .collect(joining(", "));
        var failureReason = String.format("No UI elements found in vector DB which semantically match the description '%s' with the " +
                        "similarity mainScore > %.1f. The most similar element names by similarity mainScore are: %s",
                elementDescription, MIN_TARGET_RETRIEVAL_SCORE, retrievedElementsString);
        LOG.info(failureReason);
        var message = "No elements found in DB matching the provided UI element description. Similar candidates exist but their " +
                "similarity scores are below threshold.";
        return new ElementLocationException(message, ElementLocationStatus.SIMILAR_ELEMENTS_IN_DB_BUT_SCORE_TOO_LOW);
    }

    private ElementLocationException processNoElementsFoundInDbCase(String elementDescription) {
        var failureReason = String.format("No UI elements found in vector DB which semantically match the description '%s' with the " +
                        "similarity mainScore > %.1f.",                elementDescription, MIN_GENERAL_RETRIEVAL_SCORE);
        LOG.info(failureReason);
        var message = "No elements found in DB matching the provided UI element description.";
        return new ElementLocationException(message, ElementLocationStatus.NO_ELEMENTS_FOUND_IN_DB);
    }

    private String getPageDescriptionFromModel() {
        try {
            var pageDescriptionResult = pageDescriptionAgent.executeAndGetResult(() ->
                    pageDescriptionAgent.describePage(singleImageContent(captureScreen()))).resultPayload();
            return pageDescriptionResult.pageDescription();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get page description from model", e);
        }
    }

    private ElementLocation findElementAndProcessLocationResult(Supplier<UiElementLocationInternalResult> resultSupplier,
                                                                String elementDescription) {
        var locationResult = resultSupplier.get();
        return ofNullable(locationResult.boundingBox())
                .map(_ -> processSuccessfulMatchCase(locationResult, elementDescription))
                .orElseThrow(() -> processNoVisualMatchCase(locationResult, elementDescription));
    }

    private ElementLocation processSuccessfulMatchCase(UiElementLocationInternalResult locationResult, String elementDescription) {
        var boundingBox = locationResult.boundingBox();
        LOG.info("The best visual match for the description '{}' has been located at: {}", elementDescription, boundingBox);
        var scaledBoundingBox = getScaledBoundingBox(boundingBox);
        var center = new Point((int) scaledBoundingBox.getCenterX(), (int) scaledBoundingBox.getCenterY());
        var bbox = new BoundingBox(scaledBoundingBox.x, scaledBoundingBox.y, scaledBoundingBox.x + scaledBoundingBox.width,
                scaledBoundingBox.y + scaledBoundingBox.height);
        return new ElementLocation(center.x, center.y, bbox);
    }

    private ElementLocationException processNoVisualMatchCase(UiElementLocationInternalResult locationResult, String elementDescription) {
        String rootCause;
        ElementLocationStatus status;

        if (!locationResult.algorithmicMatchFound() && !locationResult.visualGroundingMatchFound()) {
            rootCause = "Neither visual grounding nor algorithmic matching provided any results";
            status = ElementLocationStatus.ELEMENT_NOT_FOUND_ON_SCREEN_VISUAL_AND_ALGORITHMIC_FAILED;
        } else {
            if (locationResult.algorithmicMatchFound() && locationResult.visualGroundingMatchFound()) {
                rootCause = "Both visual grounding and algorithmic matching provided results, but the validation model decided that none " +
                        "of them are valid";
            } else if (locationResult.algorithmicMatchFound()) {
                rootCause = "Only algorithmic matching provided results, but the validation model decided that none of them are valid";
            } else {
                rootCause = "Only visual grounding provided results, but the validation model decided that none of them are valid";
            }
            status = ElementLocationStatus.ELEMENT_NOT_FOUND_ON_SCREEN_VALIDATION_FAILED;
        }

        var failureReason = String.format("Element with description '%s' was not found on the screen. %s.", elementDescription, rootCause);
        return new ElementLocationException(failureReason, status);
    }

    private UiElementLocationInternalResult getFinalElementLocation(UiElement elementRetrievedFromMemory,
                                                                    String elementTestData) {
        var elementScreenshot = elementRetrievedFromMemory.screenshot().toBufferedImage();
        BufferedImage wholeScreenshot = captureScreen();
        if (elementRetrievedFromMemory.zoomInRequired()) {
            LOG.info("Zoom-in is needed for element '{}'. Performing initial wide-area search.",
                    elementRetrievedFromMemory.name());
            List<Rectangle> initialCandidates = identifyBoundingBoxesUsingVision(elementRetrievedFromMemory,
                    wholeScreenshot, elementTestData);
            if (initialCandidates.isEmpty()) {
                return new UiElementLocationInternalResult(false, false, null,
                        elementRetrievedFromMemory,
                        wholeScreenshot);
            }

            var zoomInOriginalRegion = getCommonArea(initialCandidates);
            var zoomInExtendedRegion = extendZoomInRegion(zoomInOriginalRegion, elementScreenshot,
                    wholeScreenshot);
            var zoomInImage = cloneImage(
                    wholeScreenshot.getSubimage(zoomInExtendedRegion.x, zoomInExtendedRegion.y,
                            zoomInExtendedRegion.width, zoomInExtendedRegion.height));
            var scaleFactor = min(wholeScreenshot.getWidth() / ((double) zoomInImage.getWidth()),
                    AgentConfig.getElementLocatorZoomScaleFactor());
            var zoomedInScreenshot = getScaledUpImage(zoomInImage, scaleFactor);
            var elementLocationResult = getUiElementLocationResult(elementRetrievedFromMemory,
                    elementTestData,
                    zoomedInScreenshot, elementScreenshot, false);
            if (elementLocationResult.boundingBox() != null) {
                var finalBox = getActualBox(elementLocationResult.boundingBox(), zoomInExtendedRegion,
                        scaleFactor);
                return new UiElementLocationInternalResult(
                        elementLocationResult.algorithmicMatchFound(),
                        elementLocationResult.visualGroundingMatchFound(), finalBox,
                        elementLocationResult.elementUsedForLocation(),
                        wholeScreenshot);
            } else {
                return elementLocationResult;
            }
        } else {
            boolean useAlgorithmicSearch = AgentConfig.isAlgorithmicSearchEnabled()
                    && !(elementRetrievedFromMemory.isDataDependent());
            return getUiElementLocationResult(elementRetrievedFromMemory, elementTestData, wholeScreenshot,
                    elementScreenshot,
                    useAlgorithmicSearch);
        }
    }

    @NotNull
    private Rectangle getActualBox(Rectangle scaledBox, Rectangle zoomInExtendedRegion, double scaleFactor) {
        var rescaledBoundingBox = getRescaledBox(scaledBox, scaleFactor);
        var actualX = zoomInExtendedRegion.x + rescaledBoundingBox.x;
        var actualY = zoomInExtendedRegion.y + rescaledBoundingBox.y;
        return new Rectangle(actualX, actualY, rescaledBoundingBox.width, rescaledBoundingBox.height);
    }

    @NotNull
    private Rectangle getRescaledBox(Rectangle scaledBox, double scaleFactor) {
        int rescaledX = (int) (scaledBox.x / scaleFactor);
        int rescaledY = (int) (scaledBox.y / scaleFactor);
        int rescaledWidth = (int) (scaledBox.width / scaleFactor);
        int rescaledHeight = (int) (scaledBox.height / scaleFactor);
        return new Rectangle(rescaledX, rescaledY, rescaledWidth, rescaledHeight);
    }

    @NotNull
    private Rectangle extendZoomInRegion(Rectangle zoomInOriginalRegion, BufferedImage elementScreenshot, BufferedImage wholeScreenshot) {
        var extensionRatio =
                ((double) elementScreenshot.getWidth() * ZOOM_IN_EXTENSION_RATIO_PROPORTIONAL_TO_ELEMENT) / zoomInOriginalRegion.width;
        if (extensionRatio >= 1.0) {
            int newWidth = (int) (zoomInOriginalRegion.width * extensionRatio);
            int newHeight = (int) (zoomInOriginalRegion.height * extensionRatio);
            newWidth = min(newWidth, wholeScreenshot.getWidth() / 2);
            newHeight = min(newHeight, wholeScreenshot.getHeight() / 2);
            int newLeftX = Math.max(0, zoomInOriginalRegion.x - (newWidth - zoomInOriginalRegion.width) / 2);
            int newTopY = Math.max(0, zoomInOriginalRegion.y - (newHeight - zoomInOriginalRegion.height) / 2);
            int newRightX = min(wholeScreenshot.getWidth() - 1, newLeftX + newWidth);
            int newBottomY = min(wholeScreenshot.getHeight() - 1, newTopY + newHeight);
            zoomInOriginalRegion = new Rectangle(newLeftX, newTopY, newRightX - newLeftX, newBottomY - newTopY);
        }
        return zoomInOriginalRegion;
    }

    private UiElementLocationInternalResult getUiElementLocationResult(UiElement elementRetrievedFromMemory,
                                                                       String elementTestData,
                                                                       BufferedImage wholeScreenshot,
                                                                       BufferedImage elementScreenshot,
                                                                       boolean useAlgorithmicSearch) {
        var identifiedByVisionBoundingBoxes = identifyBoundingBoxesUsingVision(elementRetrievedFromMemory,
                wholeScreenshot, elementTestData);
        List<Rectangle> featureMatchedBoundingBoxes = new LinkedList<>();
        List<Rectangle> templateMatchedBoundingBoxes = new LinkedList<>();
        if (useAlgorithmicSearch) {
            var featureMatchedBoundingBoxesByElementFuture = supplyAsync(
                    () -> findMatchingRegionsWithORB(wholeScreenshot, elementScreenshot));
            var templateMatchedBoundingBoxesByElementFuture = supplyAsync(() -> mergeOverlappingRectangles(
                    findMatchingRegionsWithTemplateMatching(wholeScreenshot, elementScreenshot)));
            featureMatchedBoundingBoxes = featureMatchedBoundingBoxesByElementFuture.join();
            templateMatchedBoundingBoxes = templateMatchedBoundingBoxesByElementFuture.join();
            if (DEBUG_MODE) {
                markElementsToPlotWithBoundingBoxes(cloneImage(wholeScreenshot),
                        getElementToPlot(elementRetrievedFromMemory, featureMatchedBoundingBoxes), "opencv_features_original");
                markElementsToPlotWithBoundingBoxes(cloneImage(wholeScreenshot),
                        getElementToPlot(elementRetrievedFromMemory, templateMatchedBoundingBoxes), "opencv_template_original");
            }
        }

        return getUiElementLocationResult(elementRetrievedFromMemory, elementTestData, wholeScreenshot, identifiedByVisionBoundingBoxes,
                featureMatchedBoundingBoxes, templateMatchedBoundingBoxes);
    }

    private UiElementLocationInternalResult getUiElementLocationResult(UiElement elementRetrievedFromMemory,
                                                                       String elementTestData,
                                                                       BufferedImage wholeScreenshot,
                                                                       List<Rectangle> identifiedByVisionBoundingBoxes,
                                                                       List<Rectangle> featureMatchedBoundingBoxes,
                                                                       List<Rectangle> templateMatchedBoundingBoxes) {
        if (identifiedByVisionBoundingBoxes.isEmpty() && featureMatchedBoundingBoxes.isEmpty() &&
                templateMatchedBoundingBoxes.isEmpty()) {
            return new UiElementLocationInternalResult(false, false, null, elementRetrievedFromMemory,
                    wholeScreenshot);
        } else if (identifiedByVisionBoundingBoxes.isEmpty()) {
            LOG.info("Vision model provided no detection results, proceeding with algorithmic matches");
            return chooseBestAlgorithmicMatch(elementRetrievedFromMemory, elementTestData, wholeScreenshot, featureMatchedBoundingBoxes,
                    templateMatchedBoundingBoxes);
        } else {
            if (featureMatchedBoundingBoxes.isEmpty() && templateMatchedBoundingBoxes.isEmpty()) {
                return selectBestMatchingUiElementUsingModel(elementRetrievedFromMemory, elementTestData, identifiedByVisionBoundingBoxes,
                        wholeScreenshot, "vision_only", false, true);
            } else {
                return chooseBestCommonMatch(elementRetrievedFromMemory, elementTestData, identifiedByVisionBoundingBoxes, wholeScreenshot,
                        featureMatchedBoundingBoxes, templateMatchedBoundingBoxes)
                        .orElseGet(() -> {
                            var algorithmicIntersections = getIntersections(featureMatchedBoundingBoxes, templateMatchedBoundingBoxes);
                            if (!algorithmicIntersections.isEmpty()) {
                                var boxes = concat(identifiedByVisionBoundingBoxes.stream(), algorithmicIntersections.stream()).toList();
                                return selectBestMatchingUiElementUsingModel(elementRetrievedFromMemory, elementTestData, boxes,
                                        wholeScreenshot, "vision_and_algorithmic_only_intersections", true, true);
                            } else {
                                var boxes = Stream.of(identifiedByVisionBoundingBoxes, featureMatchedBoundingBoxes,
                                                templateMatchedBoundingBoxes)
                                        .flatMap(Collection::stream)
                                        .toList();
                                return selectBestMatchingUiElementUsingModel(elementRetrievedFromMemory, elementTestData, boxes,
                                        wholeScreenshot, "vision_and_algorithmic_regions_separately", true, true);
                            }
                        });
            }
        }
    }

    @NotNull
    private Optional<UiElementLocationInternalResult> chooseBestCommonMatch(UiElement matchingUiElement,
                                                                            String elementTestData,
                                                                            List<Rectangle> identifiedByVisionBoundingBoxes,
                                                                            BufferedImage wholeScreenshot,
                                                                            List<Rectangle> featureRects,
                                                                            List<Rectangle> templateRects) {
        LOG.info("Mapping provided by vision model results to the algorithmic ones");
        var visionAndFeatureIntersections = getIntersections(identifiedByVisionBoundingBoxes, featureRects);
        var visionAndTemplateIntersections = getIntersections(identifiedByVisionBoundingBoxes, templateRects);
        var bestIntersections = getIntersections(visionAndFeatureIntersections, visionAndTemplateIntersections);

        if (!bestIntersections.isEmpty()) {
            if (bestIntersections.size() > 1) {
                LOG.info("Found {} common vision model and algorithmic regions, using them for further refinement by " +
                        "the model.", bestIntersections.size());
                return of(selectBestMatchingUiElementUsingModel(matchingUiElement, elementTestData, bestIntersections, wholeScreenshot,
                        "intersection_all", true, true));
            } else {
                LOG.info("Found a single common vision model and common algorithmic region, returning it");
                return of(new UiElementLocationInternalResult(true, true, bestIntersections.getFirst(),
                        matchingUiElement, wholeScreenshot));
            }
        } else {
            var goodIntersections = Stream
                    .of(visionAndFeatureIntersections.stream(),
                            visionAndTemplateIntersections.stream())
                    .flatMap(Stream::distinct)
                    .toList();
            if (!goodIntersections.isEmpty()) {
                LOG.info("Found {} common regions between vision model and either template or feature matching algorithms, " +
                        "using them for further refinement by the model.", goodIntersections.size());
                return of(selectBestMatchingUiElementUsingModel(matchingUiElement, elementTestData, goodIntersections, wholeScreenshot,
                        "intersection_vision_and_one_algorithm", true, true));
            } else {
                LOG.info("Found no common regions between vision model and either template or feature matching algorithms");
                return empty();
            }
        }
    }

    private UiElementLocationInternalResult chooseBestAlgorithmicMatch(UiElement matchingUiElement,
                                                                       String elementTestData,
                                                                       BufferedImage wholeScreenshot,
                                                                       List<Rectangle> featureMatchedBoxes,
                                                                       List<Rectangle> templateMatchedBoxes) {
        if (templateMatchedBoxes.isEmpty() && featureMatchedBoxes.isEmpty()) {
            LOG.info("No algorithmic matches provided for selection");
            return new UiElementLocationInternalResult(false, false, null, matchingUiElement, wholeScreenshot);
        }

        var algorithmicIntersections = getIntersections(templateMatchedBoxes, featureMatchedBoxes);
        if (!algorithmicIntersections.isEmpty()) {
            LOG.info("Found {} common detection regions between algorithmic matches, using them for further refinement by the model.",
                    algorithmicIntersections.size());
            return selectBestMatchingUiElementUsingModel(matchingUiElement, elementTestData, algorithmicIntersections, wholeScreenshot,
                    "intersection_feature_and_template", true, false);
        } else {
            LOG.info("Found no common detection regions between algorithmic matches, using all originally detected regions for " +
                    "further refinement by the model.");
            var combinedBoundingBoxes = concat(featureMatchedBoxes.stream(), templateMatchedBoxes.stream()).toList();
            return selectBestMatchingUiElementUsingModel(matchingUiElement, elementTestData, combinedBoundingBoxes, wholeScreenshot,
                    "all_feature_and_template", true, false);
        }
    }

    private List<Rectangle> identifyBoundingBoxesUsingVision(UiElement element, BufferedImage wholeScreenshot,
                                                             String elementTestData) {
        var startTime = Instant.now();
        LOG.info("Asking model to identify bounding boxes for element '{}'.", element.name());
        try {
            var scalingRatio = getScalingRatio(wholeScreenshot);
            var imageToSend = scalingRatio < 1.0 ? scaleImage(wholeScreenshot, scalingRatio) : wholeScreenshot;
            var prompt = formatElementBoundingBoxPrompt(element, elementTestData);
            
            try (var executor = newVirtualThreadPerTaskExecutor()) {
                List<Callable<List<BoundingBox>>> tasks = range(0, VISUAL_GROUNDING_MODEL_VOTE_COUNT)
                        .mapToObj(i -> (Callable<List<BoundingBox>>) () -> elementBoundingBoxAgent.executeAndGetResult(
                                () -> elementBoundingBoxAgent.identifyBoundingBoxes(prompt, singleImageContent(imageToSend))
                        ).resultPayload().boundingBoxes())
                        .toList();
                List<Rectangle> allBoundingBoxes = executor.invokeAll(tasks).stream()
                        .map(future -> getFutureResult(future, "getting bounding boxes from vision model"))
                        .flatMap(Optional::stream)
                        .flatMap(Collection::stream)
                        .map(bb -> {
                            Rectangle rectOnScaledImage = bb.getActualBoundingBox(imageToSend.getWidth(), imageToSend.getHeight());
                            return scalingRatio < 1.0 ? getRescaledBox(rectOnScaledImage, scalingRatio) : rectOnScaledImage;
                        })
                        .filter(bb -> bb.width > 0 && bb.height > 0)
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
                    DBSCANClusterer<RectangleAdapter> clusterer =
                            new DBSCANClusterer<>(BBOX_CLUSTERING_MIN_INTERSECTION_RATIO, 0, new IoUDistance());
                    List<RectangleAdapter> points = allBoundingBoxes.stream().map(RectangleAdapter::new).toList();
                    List<Cluster<RectangleAdapter>> clusters = clusterer.cluster(points);
                    var result = clusters.stream()
                            .map(cluster -> {
                                List<Rectangle> clusterBoxes = cluster.getPoints()
                                        .stream()
                                        .map(RectangleAdapter::getRectangle)
                                        .toList();
                                return calculateAverageBoundingBox(clusterBoxes);
                            })
                            .toList();
                    if (DEBUG_MODE) {
                        var imageWithAllBoxes = cloneImage(wholeScreenshot);
                        result.forEach(box -> drawBoundingBox(imageWithAllBoxes, box, BOUNDING_BOX_COLOR));
                        saveImage(imageWithAllBoxes, "vision_identified_boxes_after_clustering");
                    }
                    LOG.info("Model identified {} bounding boxes with {} votes, resulting in {} common regions", allBoundingBoxes.size(),
                            VISUAL_GROUNDING_MODEL_VOTE_COUNT, result.size());
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
            LOG.info("Finished identifying bounding boxes using vision in {} ms", between(startTime, Instant.now()).toMillis());
        }
    }

    private Rectangle calculateAverageBoundingBox(List<Rectangle> boxes) {
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
    private List<Rectangle> getIntersections(List<Rectangle> firstSet, List<Rectangle> secondSet) {
        return firstSet.stream()
                .flatMap(r1 -> secondSet.stream()
                        .map(r1::intersection)
                        .filter(intersection -> !intersection.isEmpty()))
                .toList();
    }

    private UiElementLocationInternalResult selectBestMatchingUiElementUsingModel(UiElement uiElement,
                                                                                  String elementTestData,
                                                                                  List<Rectangle> matchedBoundingBoxes,
                                                                                  BufferedImage screenshot, String matchAlgorithm,
                                                                                  boolean algorithmicSearchDone,
                                                                                  boolean visualGroundingDone) {
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

            var successfulIdentificationResults = getValidSuccessfulIdentificationResultsFromModelUsingQuorum(
                    uiElement, elementTestData, resultingScreenshot, new ArrayList<>(boxesWithIds.keySet()));
            LOG.info("Model provided {} successful identification results for the element '{}' with {} vote(s).",
                    successfulIdentificationResults.size(), uiElement.name(), VALIDATION_MODEL_VOTE_COUNT);
            if (successfulIdentificationResults.isEmpty()) {
                return new UiElementLocationInternalResult(algorithmicSearchDone, visualGroundingDone, null, uiElement, screenshot);
            }
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
                        .map(box -> new UiElementLocationInternalResult(true, true, box, uiElement, screenshot))
                        .orElseGet(() -> new UiElementLocationInternalResult(true, false, null, uiElement, screenshot));
            } else {
                return new UiElementLocationInternalResult(true, true, boxesWithIds.get(winners.getFirst()), uiElement, screenshot);
            }
        } finally {
            LOG.info("Finished selecting best matching UI element using model in {} ms", between(startTime, Instant.now()).toMillis());
        }
    }

    @NotNull
    private List<UiElementIdentificationResult> getValidSuccessfulIdentificationResultsFromModelUsingQuorum(
            @NotNull UiElement uiElement,
            String elementTestData,
            @NotNull BufferedImage resultingScreenshot,
            @NotNull List<String> boxIds) {
        try (var executor = newVirtualThreadPerTaskExecutor()) {
            var prompt = formatElementSelectionPrompt(uiElement, elementTestData, boxIds);
            var boundingBoxColorName = CommonUtils.getColorName(BOUNDING_BOX_COLOR).toLowerCase();
            
            List<Callable<UiElementIdentificationResult>> tasks = range(0, VALIDATION_MODEL_VOTE_COUNT)
                    .mapToObj(i -> (Callable<UiElementIdentificationResult>) () -> elementSelectionAgent.executeAndGetResult(
                            () -> elementSelectionAgent.selectBestElement(boundingBoxColorName, prompt, singleImageContent(resultingScreenshot))
                    ).resultPayload())
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

    private Map<String, Rectangle> getBoxesWithIds(List<Rectangle> boundingBoxes) {
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
    private PlottedUiElement getElementToPlot(UiElement element, List<Rectangle> matchedBoundingBoxes) {
        return new PlottedUiElement(element.name(), element, getBoxesWithIds(matchedBoundingBoxes));
    }

    private void markElementsToPlotWithBoundingBoxes(BufferedImage resultingScreenshot,
                                                     PlottedUiElement elementToPlot,
                                                     String postfix) {
        var elementBoundingBoxesByLabel = elementToPlot.boundingBoxesByIds();
        drawBoundingBoxes(resultingScreenshot, elementBoundingBoxesByLabel);
        if (DEBUG_MODE) {
            saveImage(resultingScreenshot, postfix);
        }
    }

    private record PlottedUiElement(String id, UiElement uiElement, Map<String, Rectangle> boundingBoxesByIds) {
    }

    private record UiElementLocationInternalResult(boolean algorithmicMatchFound, boolean visualGroundingMatchFound,
                                                   Rectangle boundingBox, UiElement elementUsedForLocation, BufferedImage screenshot) {
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

    private static double getScalingRatio(BufferedImage image) {
        int originalWidth = image.getWidth();
        int originalHeight = image.getHeight();
        int longestSide = Math.max(originalWidth, originalHeight);
        double downscaleRatio = 1.0;
        if (longestSide > BBOX_SCREENSHOT_LONGEST_ALLOWED_DIMENSION_PIXELS) {
            downscaleRatio = ((double) BBOX_SCREENSHOT_LONGEST_ALLOWED_DIMENSION_PIXELS) / longestSide;
        }

        double originalSizeMegapixels = originalWidth * originalHeight / 1_000_000d;
        if (originalSizeMegapixels > BBOX_SCREENSHOT_MAX_SIZE_MEGAPIXELS) {
            downscaleRatio = min(downscaleRatio, Math.sqrt(BBOX_SCREENSHOT_MAX_SIZE_MEGAPIXELS / originalSizeMegapixels));
        }
        return downscaleRatio;
    }
}
