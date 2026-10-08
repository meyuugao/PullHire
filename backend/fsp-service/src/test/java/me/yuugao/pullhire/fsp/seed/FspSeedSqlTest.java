package me.yuugao.pullhire.fsp.seed;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the synthetic fsp seed ({@code docs/fsp_integration.md §4}): a present set of
 * 2-5 achievements per link spanning winner/placer/participant, a no_history set with
 * zero achievements, idempotent inserts and no event publishing (S12 concern).
 */
class FspSeedSqlTest {

    private static final Pattern ACHIEVEMENT = Pattern.compile(
            "\\(\\s*\\(SELECT id FROM fsp_links WHERE candidate_id = (\\d+)\\)\\s*,\\s*'([^']+)'\\s*,"
                    + "\\s*'([^']+)'\\s*,\\s*'([^']+)'\\s*,\\s*DATE '(\\d{4}-\\d{2}-\\d{2})'\\s*,"
                    + "\\s*(NULL|\\d+)\\s*,\\s*(NULL|'[^']+')\\s*\\)");

    @Test
    void hasPresentAndNoHistorySets() throws Exception {
        String sql = seed();

        assertThat(sql).contains("'present'", "'no_history'");
        assertThat(sql).contains("'linked'");
    }

    @Test
    void presentSetHasTwoToFiveAchievementsSpanningOutcomes() throws Exception {
        String sql = seed();

        Map<Integer, Integer> counts = new HashMap<>();
        List<String> places = new ArrayList<>();
        Matcher matcher = ACHIEVEMENT.matcher(sql);
        while (matcher.find()) {
            counts.merge(Integer.parseInt(matcher.group(1)), 1, Integer::sum);
            places.add(matcher.group(6));
        }

        assertThat(counts).hasSize(2);
        assertThat(counts.values()).allSatisfy(count -> assertThat(count).isBetween(2, 5));
        assertThat(places).contains("1");        // winner
        assertThat(places).contains("NULL");     // participant
        assertThat(places).anySatisfy(place -> assertThat(place).matches("[2-9]\\d*")); // placer
    }

    @Test
    void noHistoryCandidatesHaveNoAchievements() throws Exception {
        String sql = seed();

        Matcher matcher = ACHIEVEMENT.matcher(sql);
        List<Integer> referenced = new ArrayList<>();
        while (matcher.find()) {
            referenced.add(Integer.parseInt(matcher.group(1)));
        }

        // The no_history links (candidates 3 and 4) must not appear in the cache insert.
        assertThat(referenced).doesNotContain(3, 4);
    }

    @Test
    void insertsAreIdempotentAndDoNotPublishEvents() throws Exception {
        String sql = seed();

        assertThat(sql).contains("ON CONFLICT DO NOTHING");
        assertThat(sql).contains("ON CONFLICT (fsp_link_id, external_achievement_id) DO NOTHING");
        assertThat(sql).doesNotContain("outbox_events");
    }

    private static String seed() throws Exception {
        try (InputStream stream = FspSeedSqlTest.class.getClassLoader().getResourceAsStream("db/seed/fsp_seed.sql")) {
            assertThat(stream).as("db/seed/fsp_seed.sql on the classpath").isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
