package com.geofiber.geofiber_backend.impact;

import com.geofiber.geofiber_backend.support.IntegrationTest;
import com.geofiber.geofiber_backend.support.NetworkWiring;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static com.geofiber.geofiber_backend.support.NetworkWiring.boxJson;
import static com.geofiber.geofiber_backend.support.NetworkWiring.inputPort;
import static com.geofiber.geofiber_backend.support.NetworkWiring.outputPort;
import static com.geofiber.geofiber_backend.support.NetworkWiring.splitterId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImpactIT extends IntegrationTest {
    private static final String API = "/api/v1";

    // ---------- 1. Cabo com dois clientes na mesma caixa ----------

    @Test
    void cableImpactCountsCustomersAndTheirBox() {
        Network n = new Network();
        n.cableWithTwoCustomersInCto();

        String json = fx.getJson(API + "/impact/cables/" + n.dist, 200);

        assertEquals("CABLE", JsonPath.read(json, "$.target.kind"));
        assertEquals(2, JsonPath.<Integer>read(json, "$.totals.customers"));
        assertEquals(1, JsonPath.<Integer>read(json, "$.totals.boxes"));
        assertEquals(0, JsonPath.<Integer>read(json, "$.totals.olts"));
        assertEquals(List.of("CTO-A"), JsonPath.read(json, "$.boxes[*].label"));
        assertEquals(List.of("Cliente 1", "Cliente 2"), JsonPath.read(json, "$.boxes[0].customers[*].name"));
        assertEquals(1, JsonPath.<List<?>>read(json, "$.boxes[0].customers[0].coordinates").size());
        assertFalse(JsonPath.<Boolean>read(json, "$.truncated"));
    }

    // ---------- 2. Cliente que passa por duas caixas conta uma vez ----------

    @Test
    void customerCountsOnceEvenWhenPathCrossesTwoBoxes() {
        Network n = new Network();
        n.cableWithTwoCustomersInCto();
        n.feedThroughCeoConnector();

        String json = fx.getJson(API + "/impact/cables/" + n.feed, 200);

        assertEquals(2, JsonPath.<Integer>read(json, "$.totals.customers"));
        assertEquals(2, JsonPath.<Integer>read(json, "$.totals.boxes"));
        assertEquals(List.of("CEO-01", "CTO-A"), JsonPath.read(json, "$.boxes[*].label"));
        assertEquals(2, JsonPath.<List<?>>read(json, "$.boxes[0].customers").size());
        assertEquals(2, JsonPath.<List<?>>read(json, "$.boxes[1].customers").size());
    }

    // ---------- 3. Cabo sem clientes ----------

    @Test
    void cableWithoutCustomersHasZeroTotals() {
        Network n = new Network();
        n.cableWithTwoCustomersInCto();
        UUID empty = n.cable("CABO-VAZIO", 4);
        n.wiring.fiberIn(empty, 1);

        String json = fx.getJson(API + "/impact/cables/" + empty, 200);

        assertEquals(0, JsonPath.<Integer>read(json, "$.totals.customers"));
        assertEquals(0, JsonPath.<Integer>read(json, "$.totals.boxes"));
        assertEquals(0, JsonPath.<Integer>read(json, "$.totals.olts"));
        assertEquals(List.of(), JsonPath.read(json, "$.boxes"));
        assertEquals(List.of(), JsonPath.read(json, "$.olts"));
        assertFalse(JsonPath.<Boolean>read(json, "$.truncated"));
    }

    // ---------- 4. Caixa: clientes da própria caixa e a caixa não aparece na lista ----------

    @Test
    void boxImpactExcludesTheBoxItself() {
        Network n = new Network();
        n.cableWithTwoCustomersInCto();
        n.feedThroughCeoConnector();

        String json = fx.getJson(API + "/impact/boxes/" + n.cto, 200);

        assertEquals("BOX", JsonPath.read(json, "$.target.kind"));
        assertEquals(2, JsonPath.<Integer>read(json, "$.totals.customers"));
        assertEquals(1, JsonPath.<Integer>read(json, "$.totals.boxes"));
        assertEquals(List.of("CEO-01"), JsonPath.read(json, "$.boxes[*].label"));
        assertEquals(List.of(), JsonPath.read(json, "$.olts"));
    }

    // ---------- 5. OLT: clientes das portas PON; a própria OLT não aparece ----------

    @Test
    void oltImpactListsItsCustomersAndExcludesItself() {
        Network n = new Network();
        n.cableWithTwoCustomersInCto();
        n.feedThroughCeoConnector();
        n.feedToOlt();

        String json = fx.getJson(API + "/impact/olts/" + n.olt, 200);

        assertEquals("OLT", JsonPath.read(json, "$.target.kind"));
        assertEquals(2, JsonPath.<Integer>read(json, "$.totals.customers"));
        assertEquals(2, JsonPath.<Integer>read(json, "$.totals.boxes"));
        assertEquals(0, JsonPath.<Integer>read(json, "$.totals.olts"));
        assertEquals(List.of(), JsonPath.read(json, "$.olts"));

        // Visto por um cabo do mesmo caminho, a OLT aparece como dependência do trecho.
        String feedJson = fx.getJson(API + "/impact/cables/" + n.feed, 200);
        assertEquals(1, JsonPath.<Integer>read(feedJson, "$.totals.olts"));
        assertEquals(List.of("OLT-1"), JsonPath.read(feedJson, "$.olts[*].label"));
    }

    // ---------- 6. Cliente cancelado não conta ----------

    @Test
    void cancelledCustomerDoesNotCount() {
        Network n = new Network();
        n.cableWithTwoCustomersInCto();
        n.wiring.customer("Cliente 3", n.wiring.servicePortOf(n.output(4)));
        jdbc.update("UPDATE customer SET status = 'CANCELLED' WHERE name = 'Cliente 3'");

        String json = fx.getJson(API + "/impact/cables/" + n.dist, 200);

        assertEquals(2, JsonPath.<Integer>read(json, "$.totals.customers"));
        assertEquals(List.of("Cliente 1", "Cliente 2"), JsonPath.read(json, "$.boxes[0].customers[*].name"));
    }

    // ---------- 8. Corte em 2000 ----------

    @Test
    void listIsTruncatedAtTwoThousandButTotalsStayExact() {
        Network n = new Network();
        UUID cable = n.cable("CABO-GRANDE", 40);
        UUID[] ctos = new UUID[32];
        UUID[] fibers = new UUID[32];
        for (int k = 1; k <= 32; k++) {
            ctos[k - 1] = n.wiring.box(String.format("CTO-%02d", k), "CTO");
            fibers[k - 1] = n.wiring.fiberIn(cable, k);
        }
        n.wiring.attachChain(cable, Arrays.stream(ctos).map(NetworkWiring::boxJson).toArray(String[]::new));
        for (int k = 0; k < 32; k++) {
            String splitter = n.wiring.createSplitter(ctos[k], "RATIO_1_64", true);
            n.wiring.linkSplitterInput(ctos[k], splitterId(splitter), inputPort(splitter), fibers[k]);
        }
        jdbc.update("""
            INSERT INTO customer (id, name, contract_code, geom, status, service_port_id)
            SELECT gen_random_uuid(), 'Cliente ' || rn, 'C-' || rn, ST_SetSRID(ST_MakePoint(-46.6, -23.5), 4326), 'ACTIVE', id
            FROM (SELECT id, row_number() OVER (ORDER BY id) AS rn FROM service_port WHERE splitter_port_id IS NOT NULL) sp
            WHERE rn <= 2001
            """);

        String json = fx.getJson(API + "/impact/cables/" + cable, 200);

        assertEquals(2001, JsonPath.<Integer>read(json, "$.totals.customers"));
        assertEquals(32, JsonPath.<Integer>read(json, "$.totals.boxes"));
        assertTrue(JsonPath.<Boolean>read(json, "$.truncated"));
        assertEquals(2000, JsonPath.<List<?>>read(json, "$.boxes[*].customers[*]").size());
    }

    // ---------- 9. Geometria do alvo ----------

    @Test
    void targetCarriesItsGeometry() {
        Network n = new Network();
        n.cableWithTwoCustomersInCto();
        n.feedToOlt();

        String cableJson = fx.getJson(API + "/impact/cables/" + n.dist, 200);
        assertEquals(2, JsonPath.<List<?>>read(cableJson, "$.target.coordinates").size());
        assertEquals(-46.63, JsonPath.<Double>read(cableJson, "$.target.coordinates[0].longitude"));

        String boxJson = fx.getJson(API + "/impact/boxes/" + n.cto, 200);
        assertEquals(1, JsonPath.<List<?>>read(boxJson, "$.target.coordinates").size());
        assertEquals(-46.62, JsonPath.<Double>read(boxJson, "$.target.coordinates[0].longitude"));

        String oltJson = fx.getJson(API + "/impact/olts/" + n.olt, 200);
        assertEquals(1, JsonPath.<List<?>>read(oltJson, "$.target.coordinates").size());
    }

    // ---------- 10. Erros ----------

    @Test
    void unknownTargetIsNotFoundAndBadIdIsBadRequest() {
        String missing = fx.getJson(API + "/impact/cables/" + UUID.randomUUID(), 404);
        assertEquals("Cable not found", JsonPath.read(missing, "$.detail"));

        fx.getJson(API + "/impact/boxes/nao-e-uuid", 400);
        fx.getJson(API + "/impact/olts/nao-e-uuid", 400);
    }

    // ================= rede de teste =================

    /** Monta a rede da suíte: CTO-A com splitter 1:4, cabo DIST, cabo FEED e a OLT. */
    private final class Network {
        final NetworkWiring wiring = new NetworkWiring(fx, jdbc);
        UUID ceo;
        UUID cto;
        UUID dist;
        UUID f;
        UUID feed;
        UUID g;
        UUID olt;
        UUID oltPort;
        private String splitterJson;

        UUID cable(String name, int capacity) {
            return wiring.cable(name, capacity);
        }

        UUID output(int portNumber) {
            return outputPort(splitterJson, portNumber);
        }

        /** CTO-A com dois clientes; o cabo DIST vai da CEO-01 até a CTO-A. */
        void cableWithTwoCustomersInCto() {
            ceo = wiring.box("CEO-01", "CEO");
            cto = wiring.box("CTO-A", "CTO");
            dist = wiring.cable("CABO-DIST", 12);
            f = wiring.fiberIn(dist, 2);
            wiring.attachChain(dist, boxJson(ceo), boxJson(cto));
            splitterJson = wiring.createSplitter(cto, "RATIO_1_4", true);
            wiring.linkSplitterInput(cto, splitterId(splitterJson), inputPort(splitterJson), f);
            wiring.customer("Cliente 1", wiring.servicePortOf(output(2)));
            wiring.customer("Cliente 2", wiring.servicePortOf(output(3)));
        }

        /** Cabo FEED entra na CEO-01 e se liga ao DIST por conector: o caminho passa pelas duas caixas. */
        void feedThroughCeoConnector() {
            feed = wiring.cable("CABO-FEED", 12);
            g = wiring.fiberIn(feed, 1);
            wiring.attachChain(feed, boxJson(ceo), boxJson(cto));
            wiring.connector(ceo, f, "A", g, "B");
        }

        /** Ponta A do FEED liga-se a uma porta PON da OLT-1. */
        void feedToOlt() {
            olt = fx.olt();
            oltPort = fx.oltPort(olt, 1, 3);
            wiring.linkOltPort(g, "A", oltPort);
        }
    }
}
