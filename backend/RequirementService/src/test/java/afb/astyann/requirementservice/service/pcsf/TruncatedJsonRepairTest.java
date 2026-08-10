package afb.astyann.requirementservice.service.pcsf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parsing code is easy to get subtly wrong, and a wrong repair here would silently corrupt a PCSF
 * rather than fail loudly — so these cover the shapes a cut-off generation actually produces, not
 * just the happy one.
 */
class TruncatedJsonRepairTest {

    private final TruncatedJsonRepair repair = new TruncatedJsonRepair();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void validJsonIsReturnedUntouched() {
        JsonNode node = repair.parseTolerantly(mapper, "{\"a\":[1,2],\"b\":\"x\"}", "INF-1");

        assertThat(node).isNotNull();
        assertThat(node.get("b").asText()).isEqualTo("x");
    }

    @Test
    void recoversTheCompleteElementsWhenCutInsideAString() {
        // The reported failure: "expecting closing quote for a string value".
        String truncated = "{\"entities\":[{\"id\":\"entity_1\",\"name\":\"User\"},"
                          + "{\"id\":\"entity_2\",\"name\":\"Categ";

        JsonNode node = repair.parseTolerantly(mapper, truncated, "INF-1");

        assertThat(node).isNotNull();
        assertThat(node.get("entities").get(0).get("name").asText()).isEqualTo("User");

        // Recovery is field-granular, so the entity being written when the cut landed survives with
        // the fields it had already emitted and nothing else. That is intentional — see the class
        // javadoc — and it is inert downstream because ProjectionBuilder skips a blank name.
        assertThat(node.get("entities")).hasSize(2);
        assertThat(node.get("entities").get(1).get("id").asText()).isEqualTo("entity_2");
        assertThat(node.get("entities").get(1).has("name")).isFalse();
    }

    @Test
    void recoversWhenCutBetweenElements() {
        String truncated = "{\"endpoints\":[{\"id\":\"ep_1\"},{\"id\":\"ep_2\"},";

        JsonNode node = repair.parseTolerantly(mapper, truncated, "INF-4");

        assertThat(node.get("endpoints")).hasSize(2);
    }

    @Test
    void recoversWhenCutAfterAKeyButBeforeItsValue() {
        String truncated = "{\"a\":{\"x\":1,\"y\":2},\"b\":";

        JsonNode node = repair.parseTolerantly(mapper, truncated, "INF-3");

        assertThat(node.has("a")).isTrue();
        assertThat(node.has("b")).isFalse();
        assertThat(node.get("a").get("y").asInt()).isEqualTo(2);
    }

    @Test
    void closesSeveralLevelsOfNestingAtOnce() {
        String truncated = "{\"m\":[{\"t\":[{\"from\":\"A\",\"to\":\"B\"},{\"from\":\"C\"";

        JsonNode node = repair.parseTolerantly(mapper, truncated, "INF-1");

        assertThat(node.get("m").get(0).get("t")).hasSize(1);
        assertThat(node.get("m").get(0).get("t").get(0).get("to").asText()).isEqualTo("B");
    }

    @Test
    void anEscapedQuoteInsideAStringDoesNotEndItPrematurely() {
        // Getting this wrong would mis-track string state and cut in the wrong place.
        String truncated = "{\"a\":\"he said \\\"hi\\\" loudly\",\"b\":\"trunc";

        JsonNode node = repair.parseTolerantly(mapper, truncated, "INF-1");

        assertThat(node.get("a").asText()).isEqualTo("he said \"hi\" loudly");
        assertThat(node.has("b")).isFalse();
    }

    @Test
    void aTrailingBackslashInsideATruncatedStringIsNotTreatedAsAnEscapeOfTheCut() {
        String truncated = "{\"a\":1,\"b\":\"path\\\\";

        JsonNode node = repair.parseTolerantly(mapper, truncated, "INF-1");

        assertThat(node).isNotNull();
        assertThat(node.get("a").asInt()).isEqualTo(1);
    }

    @Test
    void returnsNullWhenNothingCompleteWasEmitted() {
        assertThat(repair.parseTolerantly(mapper, "{\"entities\":[{\"id\":\"ent", "INF-1")).isNull();
        assertThat(repair.parseTolerantly(mapper, "", "INF-1")).isNull();
        assertThat(repair.parseTolerantly(mapper, null, "INF-1")).isNull();
    }

    @Test
    void refusesTextWithMoreClosersThanOpeners() {
        // Not a truncation — something is structurally wrong, and guessing would be worse than
        // reporting nothing.
        assertThat(repair.repair("{\"a\":1}}")).isNull();
    }

    @Test
    void keepsTopLevelScalarsThatPrecedeTheCut() {
        String truncated = "{\"versionPrefix\":\"/api/v1\",\"pageSize\":20,\"name\":\"trunc";

        JsonNode node = repair.parseTolerantly(mapper, truncated, "INF-4");

        assertThat(node.get("versionPrefix").asText()).isEqualTo("/api/v1");
        assertThat(node.get("pageSize").asInt()).isEqualTo(20);
    }
}
