package com.geofiber.geofiber_backend.services.geo;

import com.geofiber.geofiber_backend.dto.geo.CoordinateDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeometryMapperTest {
    @Test
    void mapsLongitudeAndLatitudeToWgs84Point() {
        var point = GeometryMapper.toPoint(new CoordinateDto(-46.6333, -23.5505));

        assertEquals(4326, point.getSRID());
        assertEquals(-46.6333, point.getX());
        assertEquals(-23.5505, point.getY());
    }

    @Test
    void mapsLineCoordinatesWithoutSwappingAxes() {
        var line = GeometryMapper.toLineString(List.of(
            new CoordinateDto(-46.63, -23.55),
            new CoordinateDto(-46.62, -23.54)
        ));

        assertEquals(4326, line.getSRID());
        assertEquals(2, line.getNumPoints());
        assertEquals(-46.62, line.getCoordinateN(1).getX());
        assertEquals(-23.54, line.getCoordinateN(1).getY());
    }
}
