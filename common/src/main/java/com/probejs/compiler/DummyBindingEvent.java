package com.probejs.compiler;

import dev.latvian.mods.kubejs.script.BindingsEvent;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.rhino.BaseFunction;
import dev.latvian.mods.rhino.Scriptable;
import dev.latvian.mods.rhino.ScriptableObject;

import java.util.*;

public class DummyBindingEvent extends BindingsEvent {
    private final HashMap<String, Object> constantDumpMap = new HashMap<>();
    private final HashMap<String, Class<?>> classDumpMap = new HashMap<>();
    private final HashMap<String, BaseFunction> functionDump = new HashMap<>();

    public DummyBindingEvent(ScriptManager manager) {
        super(manager, null);
    }

    @Override
    public void add(String name, Object value) {
        if (value.getClass() == Class.class) {
            this.classDumpMap.put(name, (Class<?>) value);
        } else if (value instanceof BaseFunction) {
            this.functionDump.put(name, (BaseFunction) value);
        } else {
            this.constantDumpMap.put(name, value);
        }
    }

    public HashMap<String, BaseFunction> getFunctionDump() {
        return functionDump;
    }

    public HashMap<String, Class<?>> getClassDumpMap() {
        return classDumpMap;
    }

    public HashMap<String, Object> getConstantDumpMap() {
        return constantDumpMap;
    }

    public static Set<Class<?>> getConstantClassRecursive(Object constantDump) {
        Set<Class<?>> result = new HashSet<>();
        if (constantDump == null)
            return result;
        if (constantDump instanceof ScriptableObject scriptable) {
            Arrays.stream(scriptable.getIds())
                    .map(scriptable::get)
                    .map(DummyBindingEvent::getConstantClassRecursive)
                    .forEach(result::addAll);
        } else if (constantDump instanceof Map<?, ?> map) {
            map.keySet().stream().map(DummyBindingEvent::getConstantClassRecursive).forEach(result::addAll);
            map.values().stream().map(DummyBindingEvent::getConstantClassRecursive).forEach(result::addAll);
        } else if (constantDump instanceof Collection<?> collection) {
            collection.stream().map(DummyBindingEvent::getConstantClassRecursive).forEach(result::addAll);
        } else {
            result.add(constantDump.getClass());
        }
        return result;
    }
}
