package com.bookeatinglion.ai.api.config;

import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class AiPropertiesDebugTest {

    @Test
    void whichConstructorDoesSpringPick() {
        for (Constructor<?> c : AiProperties.Vector.class.getDeclaredConstructors()) {
            System.out.println("CTOR: " + c);
        }
    }

    @Test
    void bindVectorFromLocalYamlEquivalent() {
        Map<String, Object> source = new LinkedHashMap<>();
        // application.yml (base)
        source.put("app.ai.vector.index-name", "wiki-v1");
        source.put("app.ai.vector.recommendation-index-name", "recommendation-books-v1");
        source.put("app.ai.vector.distance-metric", "cosine");
        source.put("app.ai.vector.non-filterable-metadata-keys[0]", "text");
        // application-local.yml (profile override)
        source.put("app.ai.vector.bucket-name", "final-team3-wiki-vectors-prod");

        Binder binder = new Binder(new MapConfigurationPropertySource(source));
        BindResult<AiProperties.Vector> result = binder.bind("app.ai.vector", Bindable.of(AiProperties.Vector.class));

        System.out.println("bound=" + result.isBound());
        result.ifBound(v -> System.out.println("Vector: " + v));
    }
}
