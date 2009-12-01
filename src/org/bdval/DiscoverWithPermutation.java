/*
* Copyright (C) 2007-2009 Institute for Computational Biomedicine,
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
import com.martiansoftware.jsap.Switch;
import edu.cornell.med.icb.geo.tools.ClassificationTask;
import edu.cornell.med.icb.geo.tools.GEOPlatform;
import edu.cornell.med.icb.geo.tools.GeneList;
import edu.cornell.med.icb.geo.tools.MicroarrayTrainEvaluate;
import edu.cornell.med.icb.tissueinfo.similarity.ScoredTranscriptBoundedSizeQueue;
import edu.cornell.med.icb.tissueinfo.similarity.TranscriptScore;
import edu.cornell.med.icb.util.RandomAdapter;
import edu.mssm.crover.tables.*;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import it.unimi.dsi.lang.MutableString;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;


/**
 * Univariate feature selection with Permutation Test.
 * We compare each feature individually for evidence of differential distribution in the two prediction classes.
 * If the adhoc test statistic p-Value is less than
 * the confidence interval, then the feature is selected as an informative biomarker.
 *
 * @author Nyasha Chambwe Date: Sep 16, 2009 Time: 3:34:53 PM
 */

public class DiscoverWithPermutation extends DAVMode {

    private static final Log LOG = LogFactory.getLog(DiscoverWithPermutation.class);
    private boolean writeGeneListFormat;
    private int maxProbesToReport;
    private FeatureReporting reporter;
    private double alpha;
    final Object2DoubleMap<MutableString> probesetPvalues =
            new Object2DoubleOpenHashMap<MutableString>();



    @Override
    public void interpretArguments(final JSAP jsap, final JSAPResult result,
                                   final DAVOptions options) {
        super.interpretArguments(jsap, result, options);

        alpha = result.getDouble("alpha");
        writeGeneListFormat = result.getBoolean("output-gene-list");
        reporter = new FeatureReporting(writeGeneListFormat);
        for (final GEOPlatform platform : options.platforms) {
            maxProbesToReport += platform.getProbesetCount();
        }
        if (result.contains("report-max-probes")) {
            maxProbesToReport = result.getInt("report-max-probes");
            LOG.info("Output will be restricted to the " + maxProbesToReport
                    + " probesets with smaller p-value.");
        }
    }

    /**
     * Define command line options for this mode.
     *
     * @param jsap the JSAP command line parser
     * @throws com.martiansoftware.jsap.JSAPException
     *          if there is a problem building the options
     */
    @Override
    public void defineOptions(final JSAP jsap) throws JSAPException {
        final Parameter numFeatureParam = new FlaggedOption("alpha")
                .setStringParser(JSAP.DOUBLE_PARSER)
                .setDefault("0.05")
                .setRequired(true)
                .setLongFlag("alpha")
                .setShortFlag('n')
                .setHelp("The significance level for the permutation test p-value. "
                        + "Default 0.05/5% confidence level.");
        jsap.registerParameter(numFeatureParam);


        final Parameter outputGeneList = new Switch("output-gene-list")
                .setLongFlag("output-gene-list")
                .setHelp("Write features to the output in the tissueinfo gene list format.");
        jsap.registerParameter(outputGeneList);

        final Parameter reportMaxProbes = new FlaggedOption("report-max-probes")
                .setStringParser(JSAP.INTEGER_PARSER)
                .setLongFlag("report-max-probes")
                .setHelp("Restrict output to the top ranked probes. This option works in "
                        + "conjunction with alpha and can further restrict the output.");
        jsap.registerParameter(reportMaxProbes);
    }

    @Override
    public void process(final DAVOptions options) {

        super.process(options);

        for (final ClassificationTask task : options.classificationTasks) {
            // classification task is synonymous with the endpoints eg. GSE-fusion-YesNo
            //i.e. for every endpoint defined in the task file

            for (final GeneList geneList : options.geneLists) {

                System.out.println("Discover markers by permutation " + task);

                options.normalizeFeatures = false;

                options.scaleFeatures = false;
                Table processedTable = null;
                try {
                    processedTable = processTable(geneList, options.inputTable,
                            options, MicroarrayTrainEvaluate.calculateLabelValueGroups(task));
                } catch (ColumnTypeException e) {
                    error(e);
                } catch (TypeMismatchException e) {
                    error(e);
                } catch (InvalidColumnException e) {
                    error(e);
                }
                final ArrayTable.ColumnDescription labelColumn =
                        processedTable.getColumnValues(0);

                //System.out.println(processedTable.toString(processedTable,false));

                assert labelColumn.type == String.class : "label must have type String";

                String[] labels = labelColumn.getStrings(); // labels are the sampleIDs

                final List<Set<String>> labelValueGroups =
                        MicroarrayTrainEvaluate.calculateLabelValueGroups(task);

                final ScoredTranscriptBoundedSizeQueue selectedProbesets =
                        new ScoredTranscriptBoundedSizeQueue(maxProbesToReport);

                // for all probesets across all samples
                for (int featureIndex = 2; featureIndex < processedTable.getColumnNumber(); featureIndex++) {
                    final int probesetIndex = featureIndex - 1;
                    double testStatistic;
                    testStatistic = evaluateStatisticForLabels(processedTable, labels, labelValueGroups, featureIndex);

                    DoubleList permutedStatsList = new DoubleArrayList();
                    // double arraylist to store all permuted test statistics


                    int i = 0;
                    double permutedStatistic;
                    List<String> labelsList = Arrays.asList(labels);
                    RandomAdapter randomAdapter;

                    while (i < 1000) {

                        // now need to shuffle the labels
                        randomAdapter = new RandomAdapter(options.randomGenerator);
                        Collections.shuffle(labelsList, randomAdapter);

                        String[] shuffledLabels = (String[]) labelsList.toArray();
                        permutedStatistic = evaluateStatisticForLabels(processedTable, shuffledLabels, labelValueGroups, featureIndex);
                        permutedStatsList.add(permutedStatistic);
                        i++;
                    }

                    double pValue;
                    // calculate pvalue
                    double frequency = 0;
                    int z = 0;
                    while (z < permutedStatsList.size()) {
                       // System.out.println("RandomStat " + permutedStatsList.get(z) + " Teststat " + testStatistic);
                        if (permutedStatsList.get(z) > testStatistic) {
                            frequency++;
                        }
                        z++;
                    }
                   // System.out.println("frequency " + frequency);
                    pValue = frequency/1000;

                    if (pValue != pValue) {
                        //NaN
                        pValue = 1;
                    }

                    // save in probesetPValues map
                    final MutableString probesetId = options.getProbesetIdentifier(probesetIndex);
                    probesetPvalues.put(probesetId.copy().compact(), pValue);

                    if (pValue <= alpha) {
                        System.out.println("probeset Id: " + probesetId + "  pValue:" + pValue);
                        // The queue keeps items with larger score. Transform the pValue accordingly.
                        selectedProbesets.enqueue(new TranscriptScore(1 - pValue, probesetIndex));
                    }
                }


                System.out.println("Selected " + selectedProbesets.size()
                        + " probesets at significance level=" + Double
                        .toString(alpha));
                while (!selectedProbesets.isEmpty()) {
                    final TranscriptScore feature = selectedProbesets.dequeue();
                    final TranscriptScore probeset =
                            new TranscriptScore(feature.score, feature.transcriptIndex);

                    reporter.reportFeature(0, -(feature.score - 1), probeset, options);
                }

            }
        }
    }

    private double evaluateStatisticForLabels(Table processedTable, String[] labels, List<Set<String>> labelValueGroups, int featureIndex) {
        final ArrayTable.ColumnDescription cd = processedTable.getColumnValues(featureIndex);
        assert cd.type == double.class : "features must have type double";
        final double[] values = cd.getDoubles();
        final DoubleList positiveLabelValues = new DoubleArrayList();
        final DoubleList negativeLabelValues = new DoubleArrayList();
        int index = 0;

        for (final String label : labels) {
            if (labelValueGroups.get(0).contains(label)) {
                negativeLabelValues.add(values[index]);
            } else {
                if (labelValueGroups.get(1).contains(label)) {
                    positiveLabelValues.add(values[index]);
                }
            }
            index++;
        }

        double[] valuesForPositiveLabel = positiveLabelValues.toDoubleArray();
        double[] valuesForNegativeLabel = negativeLabelValues.toDoubleArray();
        return evaluateStatistic(valuesForPositiveLabel, valuesForNegativeLabel);
    }

    private double evaluateStatistic(double[] valuesForPositiveLabel, double[] valuesForNegativeLabel) {

        // sum of expression values for positive label
        double sumPositiveLabelValues = 0;

        for (double positiveValue : valuesForPositiveLabel) {
            sumPositiveLabelValues += positiveValue;
        }
        double avgPositiveLabelValues = (sumPositiveLabelValues) / valuesForPositiveLabel.length;

        double sumNegativeLabelValues = 0;
        for (double negativeValues : valuesForNegativeLabel) {
            sumNegativeLabelValues += negativeValues;
        }

        double avgNegativeLabelValues = sumNegativeLabelValues / valuesForNegativeLabel.length;

        return Math.abs(avgPositiveLabelValues - avgNegativeLabelValues);
    }


    private void error(Exception e) {
        System.err.println("Cannot processTable. Details may be provided below.");
        e.printStackTrace();
        System.exit(10);
    }
}
