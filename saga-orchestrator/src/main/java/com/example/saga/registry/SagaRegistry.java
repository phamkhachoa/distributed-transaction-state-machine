package com.example.saga.registry;

import com.example.saga.definition.SagaDefinition;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component
public class SagaRegistry {
    
    private final Map<String, SagaDefinition<?, ?>> sagaDefinitions = new HashMap<>();
    
    public void registerSaga(SagaDefinition<?, ?> sagaDefinition) {
        sagaDefinitions.put(sagaDefinition.getSagaType(), sagaDefinition);
    }
    
    public SagaDefinition<?, ?> getSagaDefinition(String sagaType) {
        SagaDefinition<?, ?> definition = sagaDefinitions.get(sagaType);
        if (definition == null) {
            throw new IllegalArgumentException("No saga definition found for type: " + sagaType);
        }
        return definition;
    }
    
    public Set<String> getSagaTypes() {
        return sagaDefinitions.keySet();
    }
    
    public boolean hasSagaType(String sagaType) {
        return sagaDefinitions.containsKey(sagaType);
    }
} 