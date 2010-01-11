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

package edu.cornell.med.icb.geo;

import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.lang.MutableString;

/**
 * @author Fabien Campagne Date: Aug 16, 2007 Time: 3:00:38 PM
 */
public interface FormatAdapter {
    SampleDataCallback getCallback(GEOPlatformIndexed platform);

    void analyzeSampleData(GEOPlatformIndexed platform,
                           SampleDataCallback callback,
                           MutableString sampleIdentifier);

    void preSeries(GEOPlatformIndexed platform);

    void postSeries(GEOPlatformIndexed platform,
                    ObjectList<MutableString> sampleIdSelection);

    void setOptions(GeoScanOptions options);
}
