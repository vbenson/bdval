/*
 * Copyright (C) 2008-2009 Institute for Computational Biomedicine,
 *                         Weill Medical College of Cornell University
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.bdval;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import edu.cornell.med.icb.learning.tools.svmlight.EvaluationMeasure;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bdval.modelconditions.RestatMode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Calculates statistics from a table of predictions generated by Predict.
 * @deprecated The bdval stats mode has been rewritten as org.bdval.modelconditions.RestatMode
 * @author Fabien Campagne
 *         Date: Apr 6, 2008
 *         Time: 10:18:17 AM
 */
public class StatsMode extends Predict {
    /**
     * Used to log debug and error messages.
     */
    private static final Log LOG = LogFactory.getLog(StatsMode.class);

    private String predictionsFilename;
    private final MaqciiHelper maqciiHelper = new MaqciiHelper();
    private String survivalFileName;

    RestatMode.StatsEvaluationType statsEvalType;

    @Override
    public void interpretArguments(final JSAP jsap, final JSAPResult result,
                                   final DAVOptions options) {
        predictionsFilename = result.getString("predictions");
        options.datasetName = result.getString("dataset-name");

        final String filename = new File(predictionsFilename).getName();
        if (options.datasetName.equals("auto")) {
            options.datasetName = filename.substring(0, filename.indexOf('-'));
        }

        options.modelId = result.getString("model-id");
        if ("no_model_id".equals(options.modelId)) {
            // try to guess from predictions filename:
            // e.g., Cologne_OS_MO-genelists-NC01-2000-svmglobal-SIYCT-prediction-table.txt
            final Pattern pattern = Pattern.compile(".*(.....)-prediction-table.txt");

            final Matcher matcher = pattern.matcher(filename);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Can't extract model id from " + filename);
            }
            options.modelId = matcher.group(1);
        }

        if (result.contains("survival")) {
            survivalFileName = result.getString("survival");
            LOG.debug("Survival file:" + survivalFileName);
        }

        if ("auto".equals(result.getString("label"))) {
            final String label = guessLabel(options.datasetName, filename);
            maqciiHelper.setupSubmissionFile(result, options, label);
        } else {
            maqciiHelper.setupSubmissionFile(result, options);
        }

        final String aggregationMethod = result.getString("aggregation-method");
        if (aggregationMethod == null) {
            statsEvalType = RestatMode.StatsEvaluationType.STATS_PER_REPEAT;
        } else {
            if ("per-repeat".equalsIgnoreCase(aggregationMethod)) {
                statsEvalType = RestatMode.StatsEvaluationType.STATS_PER_REPEAT;
            } else if ("per-test-set".equalsIgnoreCase(aggregationMethod)) {
                statsEvalType = RestatMode.StatsEvaluationType.STATS_PER_SPLIT;
            } else {
                System.err.println("Cannot parse argument of option --aggregation-method");
                System.exit(1);
            }
        }
    }

    public static String guessLabel(final String datasetName, final String filename) {
        return guessLabel(datasetName, filename, "-(.*)-(.....)-prediction-table.txt");
    }

    public static String guessLabel(final String datasetName, final String filename,
                                    final String patternTemplate) {
        // try to guess from predictions filename:
        // e.g., Cologne_OS_MO-genelists-NC01-2000-svmglobal-SIYCT-prediction-table.txt
        final Pattern pattern = Pattern.compile(datasetName + patternTemplate);
        final Matcher matcher = pattern.matcher(filename);
        final String label;
        if (matcher.matches()) {
            label = matcher.group(1);
        } else {
            label = null;
        }
        return label;
    }

    /**
     * Define command line options for this mode.
     *
     * @param jsap the JSAP command line parser
     * @throws JSAPException if there is a problem building the options
     */
    @Override
    public void defineOptions(final JSAP jsap) throws JSAPException {

        System.err.println("The stats mode in bdval.jar is now obsolete. Please use the ant script restat target, or see org.bdval.modelconditions.ProcessModelConditions");

        System.exit(1);
        final Parameter survivalFilenameOption = new FlaggedOption("survival")
                .setStringParser(JSAP.STRING_PARSER)
                .setDefault(JSAP.NO_DEFAULT)
                .setRequired(false)
                .setLongFlag("survival")
                .setHelp("Survival filename. This file contains survival data "
                        + "in tab delimited format; column 1: chipID has to match cids and "
                        + "tmm, column 2: time to event, column 3 censor with 1 as event 0 "
                        + "as censor, column 4 and beyond are numerical covariates that "
                        + "will be included in the regression model");
        jsap.registerParameter(survivalFilenameOption);

        // there is no need for task definitions.
        jsap.getByID("task-list").addDefault("N/A");
        // there is no need for condition ids.
        jsap.getByID("conditions").addDefault("N/A");
        // there is no need for random seed.
        jsap.getByID("seed").addDefault("1");
        // there is no need for a gene list. The model has enough information to recreate it.
        jsap.getByID("gene-lists").addDefault("N/A");
        final Parameter inputFilenameOption = new FlaggedOption("predictions")
                .setStringParser(JSAP.STRING_PARSER)
                .setDefault(JSAP.NO_DEFAULT)
                .setRequired(true)
                .setLongFlag("predictions")
                .setHelp("Filename that contains the predictions "
                        + "in the format written by --mode predict.");
        jsap.registerParameter(inputFilenameOption);
        jsap.getByID("input").addDefault("N/A");
        jsap.getByID("platform-filenames").addDefault("N/A");

        final Parameter typeOfSplitHandling = new FlaggedOption("aggregation-method")
                .setStringParser(JSAP.STRING_PARSER)
                .setDefault("per-repeat")
                .setRequired(false)
                .setLongFlag("aggregation-method")
                .setHelp("Type of aggregation method. Predictions can be aggregated by repeat of cross-validation " +
                        "(per-repeat, default value of this option), or by test set (per-test-set). ");
        jsap.registerParameter(typeOfSplitHandling);

        maqciiHelper.defineSubmissionFileOption(jsap);
    }

    @Override
    public void process(final DAVOptions options) {
            evaluateStats(options, predictionsFilename);


    }

    private void evaluateStats(final DAVOptions options, String predictionsFilename) {
        PredictedItems predictions=null;
        try {

            predictions = new PredictedItems();
            predictions.load(predictionsFilename);
        } catch (IOException e) {
            LOG.fatal("An error occurred reading predictions file " + predictionsFilename, e);
            System.exit(1);
        }
        final List<SurvivalMeasures> survivalMeasuresList = new ArrayList<SurvivalMeasures>();
        LOG.info("Calculating statistics for predictions in file " + this.predictionsFilename);
        final int numberOfRepeats = predictions.getNumberOfRepeats();
        final ObjectSet<CharSequence> evaluationMeasureNames = new ObjectArraySet<CharSequence>();
        evaluationMeasureNames.addAll(Arrays.asList(MEASURES));
        final EvaluationMeasure repeatedEvaluationMeasure = new EvaluationMeasure();

        switch (statsEvalType) {
            case STATS_PER_REPEAT:
                RestatMode.evaluatePerformanceMeasurePerRepeat(predictions, survivalFileName, survivalMeasuresList, numberOfRepeats,
                        evaluationMeasureNames, repeatedEvaluationMeasure);
                break;
            case STATS_PER_SPLIT:
                RestatMode.evaluatePerformanceMeasurePerTestSet(predictions, survivalFileName, survivalMeasuresList, numberOfRepeats,
                        evaluationMeasureNames, repeatedEvaluationMeasure);
                break;
        }

        final int numberOfFeatures = predictions.modelNumFeatures();
        maqciiHelper.printSubmissionHeaders(options, survivalFileName != null);

        maqciiHelper.printSubmissionResults(options, repeatedEvaluationMeasure,
                numberOfFeatures, numberOfRepeats, survivalMeasuresList);
        LOG.info(String.format("Overall: %s", repeatedEvaluationMeasure.toString()));
    }

}
