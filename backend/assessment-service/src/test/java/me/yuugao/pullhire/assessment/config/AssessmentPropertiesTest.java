package me.yuugao.pullhire.assessment.config;

import java.time.Period;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves every numeric grade-test parameter (cut-scores, SE threshold, item
 * min/max, start θ, cooldown, exposure) is externalized under
 * {@code app.assessment.*} and overridable, by binding a test yml onto
 * {@link AssessmentProperties} (OQ-1; cooldown decided Q-3).
 */
class AssessmentPropertiesTest {

    @Test
    void everyNumericParameterBindsFromExternalConfig() throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        for (PropertySource<?> source : new YamlPropertySourceLoader()
                .load("assessment-properties-test", new ClassPathResource("assessment-properties-test.yml"))) {
            environment.getPropertySources().addLast(source);
        }

        Binder binder = new Binder(
                ConfigurationPropertySources.get(environment),
                new PropertySourcesPlaceholdersResolver(environment),
                ApplicationConversionService.getSharedInstance());

        AssessmentProperties properties = binder
                .bind("app.assessment", Bindable.of(AssessmentProperties.class))
                .get();

        assertThat(properties.getSeThreshold()).isEqualTo(0.42);
        assertThat(properties.getCiConfidence()).isEqualTo(1.645);
        assertThat(properties.getMinItems()).isEqualTo(12);
        assertThat(properties.getMaxItems()).isEqualTo(40);
        assertThat(properties.getExposureDefault()).isEqualTo(55);
        assertThat(properties.getCooldownDuration()).isEqualTo(Period.ofMonths(2));
        assertThat(properties.getGrades())
                .containsExactly("intern", "junior", "middle", "senior", "lead", "staff");
        assertThat(properties.getCutScores())
                .containsEntry("intern", -1.75)
                .containsEntry("junior", -0.75)
                .containsEntry("middle", 0.25)
                .containsEntry("senior", 1.25)
                .containsEntry("lead", 2.75);
        assertThat(properties.getStartThetaByGrade())
                .containsEntry("intern", -1.25)
                .containsEntry("junior", -0.75)
                .containsEntry("middle", -0.25)
                .containsEntry("senior", 0.25)
                .containsEntry("lead", 0.75);
    }

    @Test
    void defaultsCoverEveryGradeAndDecidedCooldown() {
        AssessmentProperties properties = new AssessmentProperties();

        assertThat(properties.getCutScores())
                .containsKeys("intern", "junior", "middle", "senior", "lead");
        assertThat(properties.getGrades())
                .containsExactly("intern", "junior", "middle", "senior", "lead");
        assertThat(properties.getStartThetaByGrade())
                .containsKeys("intern", "junior", "middle", "senior", "lead");
        assertThat(properties.getCooldownDuration()).isEqualTo(Period.ofMonths(1));
    }
}
