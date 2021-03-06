/*
 * Copyright (C) 2007-2010 Institute for Computational Biomedicine,
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

package edu.cornell.med.icb.geo.mpss;

import edu.cornell.med.icb.geo.FormatAdapter;
import edu.cornell.med.icb.geo.GEOPlatformIndexed;
import edu.cornell.med.icb.geo.GeoScanOptions;
import edu.cornell.med.icb.geo.SampleDataCallback;
import edu.cornell.med.icb.identifier.IndexedIdentifier;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.lang.MutableString;

import java.io.IOException;

/**
 * @author Fabien Campagne Date: Aug 29, 2007 Time: 3:29:15 PM
 */
public class Tags2ExternalId implements FormatAdapter {

    public SampleDataCallback getCallback(final GEOPlatformIndexed platform) {
        return null;
    }

    public void analyzeSampleData(final GEOPlatformIndexed platform,
                                  final SampleDataCallback callback,
                                  final MutableString sampleIdentifier) {
    }

    public void preSeries(final GEOPlatformIndexed platform) {

        final IndexedIdentifier probeIds = platform.getProbeIds();
        try {
            for (final MutableString tag : probeIds.keySet()) {

                tag.write(options.output);
                options.output.write('\t');
                platform.getExternalId(platform.getExternalIndexForProbeId(tag)).write(options.output);
                options.output.write('\n');
            }
        } catch (IOException e) {

            System.out.println("Error writting to output file.");
            System.exit(10);
        }
        options.output.close();
        System.exit(0);
    }

    public void postSeries(final GEOPlatformIndexed platform, final ObjectList<MutableString> sampleIdSelection) {

    }

    GeoScanOptions options;

    public void setOptions(final GeoScanOptions options) {
        this.options = options;
    }

}
