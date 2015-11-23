/*
 * Copyright (C) 2015 QAware GmbH
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package de.qaware.chronix.converter;

import de.qaware.chronix.dts.MetricDataPoint;
import de.qaware.chronix.schema.MetricTSSchema;
import de.qaware.chronix.serializer.JsonKassiopeiaSimpleSerializer;
import de.qaware.chronix.timeseries.MetricTimeSeries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.Collection;

/**
 * The kassiopeia document converter for the simple time series class
 *
 * @author f.lautenschlager
 */
public class KassiopeiaSimpleConverter implements DocumentConverter<MetricTimeSeries> {

    private static final Logger LOGGER = LoggerFactory.getLogger(KassiopeiaSimpleConverter.class);

    @Override
    public MetricTimeSeries from(BinaryStorageDocument binaryStorageDocument, long queryStart, long queryEnd) {

        //First decompress
        byte[] decompressed = Compression.decompress(binaryStorageDocument.getData());

        //Second deserialize
        JsonKassiopeiaSimpleSerializer serializer = new JsonKassiopeiaSimpleSerializer();
        Collection<MetricDataPoint> points = serializer.fromJson(new String(decompressed));

        //get the metric
        String metric = binaryStorageDocument.get(MetricTSSchema.METRIC).toString();

        //Third build a minimal time series
        MetricTimeSeries.Builder builder = new MetricTimeSeries.Builder(metric)
                .start(binaryStorageDocument.getStart())
                .end(binaryStorageDocument.getEnd())
                .data(points);

        //add all user defined attributes
        binaryStorageDocument.getFields().forEach((field, value) -> {
            if (MetricTSSchema.isUserDefined(field)) {
                builder.attribute(field, value);
            }
        });

        return builder.build();
    }


    @Override
    public BinaryStorageDocument to(MetricTimeSeries document) {
        BinaryStorageDocument.Builder builder = new BinaryStorageDocument.Builder();

        try {
            JsonKassiopeiaSimpleSerializer serializer = new JsonKassiopeiaSimpleSerializer();
            //serialize
            String json = serializer.toJson(document.getPoints());
            byte[] data = Compression.compress(json.getBytes("UTF-8"));

            //Add the minimum required fields
            builder.start(document.getStart())
                    .end(document.getEnd())
                    .data(data);

            //Currently we only have a metric
            builder.field(MetricTSSchema.METRIC, document.getMetric());

            //Add a list of user defined attributes
            document.attributes().forEach(builder::field);

        } catch (UnsupportedEncodingException e) {
            LOGGER.error("Could not encode json string", e);
        }

        return builder.build();
    }
}