package com.geofiber.geofiber_backend.cable;

import com.geofiber.geofiber_backend.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CableAttachmentIT extends IntegrationTest {
    private static final String API = "/api/v1";

    private String attachments(UUID cable) {
        return API + "/cables/" + cable + "/attachments";
    }

    private static String start(String key, UUID id) {
        return "{\"role\":\"START\",\"" + key + "\":\"" + id + "\"}";
    }

    private static String pass(UUID box) {
        return "{\"role\":\"PASS_THROUGH\",\"boxId\":\"" + box + "\"}";
    }

    private static String end(UUID box) {
        return "{\"role\":\"END\",\"boxId\":\"" + box + "\"}";
    }

    @Test
    void replacesAndListsAttachmentsInOrder() {
        UUID olt = fx.olt();
        UUID ceo = fx.box("CEO", null, 2);
        UUID cto = fx.box("CTO", 8, 1);
        UUID cable = fx.cable(12);

        fx.putJson(attachments(cable), "[" + start("oltId", olt) + "," + pass(ceo) + "," + end(cto) + "]", 200);
        String body = fx.putJson(attachments(cable), "[" + start("oltId", olt) + "," + pass(ceo) + "," + end(cto) + "]", 200);
        String listed = mvcGet(attachments(cable));

        assertEquals(body, listed);
        assertEquals(List.of(1, 2, 3), JsonPath.read(listed, "$[*].sequenceNumber"));
        assertEquals(List.of("OLT-1", "CEO-1", "CTO-1"), JsonPath.read(listed, "$[*].elementName"));
        assertEquals(cto.toString(), JsonPath.read(listed, "$[2].boxId"));
    }

    @Test
    void rejectsAttachmentWithBothOrNoElement() {
        UUID olt = fx.olt();
        UUID box = fx.box("CTO", 8, 1);
        UUID cable = fx.cable(12);

        fx.putJson(attachments(cable),
            "[{\"role\":\"START\",\"oltId\":\"" + olt + "\",\"boxId\":\"" + box + "\"}," + end(box) + "]", 400);
        fx.putJson(attachments(cable), "[{\"role\":\"START\"}," + end(box) + "]", 400);
    }

    @Test
    void rejectsListWithoutExactlyOneStartAndOneEnd() {
        UUID a = fx.box("CEO", null, 1);
        UUID b = fx.box("CTO", 8, 1);
        UUID cable = fx.cable(12);

        fx.putJson(attachments(cable), "[" + start("boxId", a) + "]", 400);
        fx.putJson(attachments(cable), "[" + pass(a) + "," + end(b) + "]", 400);
        fx.putJson(attachments(cable), "[" + start("boxId", a) + "," + pass(b) + "]", 400);
        fx.putJson(attachments(cable), "[]", 200);
    }

    @Test
    void oltPortRejectsFiberFromUnattachedCable() {
        UUID olt = fx.olt();
        UUID cable = fx.cable(12);
        UUID fiber = fx.fiber(fx.tube(cable, 1, 12), 1, 1);

        String body = fx.postJson(API + "/olts/" + olt + "/ports",
            "{\"slotNumber\":1,\"portNumber\":1,\"fiberId\":\"" + fiber + "\"}", 409);
        assertEquals("Cable is not attached to this OLT", JsonPath.read(body, "$.detail"));
    }

    @Test
    void spliceRejectsFiberFromCableNotAttachedToBox() {
        UUID box = fx.box("CEO", null, 1);
        fx.tray(box, 1, 12);
        UUID attached = fx.cable(12);
        UUID other = fx.cable(12);
        fx.putJson(attachments(attached), "[" + start("boxId", box) + "," + end(fx.box("CTO", 8, 1)) + "]", 200);
        UUID in = fx.fiber(fx.tube(attached, 1, 12), 1, 1);
        UUID out = fx.fiber(fx.tube(other, 1, 12), 1, 1);

        String body = fx.postJson(API + "/boxes/" + box + "/splices",
            "{\"trayNumber\":1,\"inputFiberId\":\"" + in + "\",\"outputFiberId\":\"" + out + "\"}", 409);
        assertEquals("Cable is not attached to this box", JsonPath.read(body, "$.detail"));
    }

    @Test
    void splitterPortRejectsFiberFromCableNotAttachedToBox() {
        UUID box = fx.box("CEO", null, 1);
        String splitter = fx.postJson(API + "/boxes/" + box + "/splitters", "{\"ratio\":\"RATIO_1_2\",\"balanced\":true}", 201);
        String input = JsonPath.read(splitter, "$.ports[0].id");
        UUID fiber = fx.fiber(fx.tube(fx.cable(12), 1, 12), 1, 1);

        String body = fx.putJson(API + "/splitters/" + JsonPath.read(splitter, "$.id") + "/ports/" + input,
            "{\"fiberId\":\"" + fiber + "\"}", 409);
        assertEquals("Cable is not attached to this box", JsonPath.read(body, "$.detail"));
    }

    @Test
    void cannotDetachBoxWhileFiberIsConnectedThere() {
        UUID ceo = fx.box("CEO", null, 1);
        UUID cto = fx.box("CTO", 8, 1);
        fx.tray(ceo, 1, 12);
        UUID cable = fx.cable(12);
        String original = "[" + start("boxId", ceo) + "," + end(cto) + "]";
        fx.putJson(attachments(cable), original, 200);
        UUID tube = fx.tube(cable, 1, 12);
        UUID in = fx.fiber(tube, 1, 1);
        UUID out = fx.fiber(tube, 2, 2);
        fx.postJson(API + "/boxes/" + ceo + "/splices",
            "{\"trayNumber\":1,\"inputFiberId\":\"" + in + "\",\"outputFiberId\":\"" + out + "\"}", 201);
        UUID other = fx.box("CEO", null, 1);

        String body = fx.putJson(attachments(cable), "[" + start("boxId", other) + "," + end(cto) + "]", 409);

        assertEquals("Cable still has fibers connected at a detached element", JsonPath.read(body, "$.detail"));
        assertEquals(List.of(ceo.toString(), cto.toString()), JsonPath.read(mvcGet(attachments(cable)), "$[*].boxId"));
    }

    private String mvcGet(String url) {
        try {
            return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url))
                .andReturn().getResponse().getContentAsString();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
