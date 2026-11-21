package com.geofiber.geofiber_backend.services.geo;

import com.geofiber.geofiber_backend.dto.geo.CoordinateDto;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.util.List;

public final class GeometryMapper {
    private static final GeometryFactory GEOMETRY_FACTORY =
        new GeometryFactory(new PrecisionModel(), 4326);

    private GeometryMapper() {
    }

    public static Point toPoint(CoordinateDto coordinate) {
        return GEOMETRY_FACTORY.createPoint(toCoordinate(coordinate));
    }

    public static LineString toLineString(List<CoordinateDto> coordinates) {
        Coordinate[] points = coordinates.stream()
            .map(GeometryMapper::toCoordinate)
            .toArray(Coordinate[]::new);
        return GEOMETRY_FACTORY.createLineString(points);
    }

    public static CoordinateDto fromPoint(Point point) {
        return new CoordinateDto(point.getX(), point.getY());
    }

    public static List<CoordinateDto> fromLineString(LineString lineString) {
        return java.util.Arrays.stream(lineString.getCoordinates())
            .map(coordinate -> new CoordinateDto(coordinate.getX(), coordinate.getY()))
            .toList();
    }

    private static Coordinate toCoordinate(CoordinateDto coordinate) {
        return new Coordinate(coordinate.longitude(), coordinate.latitude());
    }
}
