package com.probejs.info;


import com.probejs.ProbeCommands;
import com.probejs.ProbeConfig;
import com.probejs.ProbeJS;
import com.probejs.formatter.ClassResolver;
import com.probejs.formatter.NameResolver;
import com.probejs.info.type.ITypeInfo;
import com.probejs.info.type.InfoTypeResolver;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.JavaMembers;
import dev.latvian.mods.rhino.util.HideFromJS;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

public class ClassInfo {
    public static final Map<Class<?>, ClassInfo> CLASS_CACHE = new HashMap<>();
    public static final Map<String, ClassInfo> CLASS_NAME_CACHE = new HashMap<>();

    public static ClassInfo getOrCache(Class<?> clazz) {
        //No computeIfAbsent because new ClassInfo will call getOrCache for superclass lookup
        //This will cause a CME because multiple updates occurred in one computeIfAbsent
        if (CLASS_CACHE.containsKey(clazz))
            return CLASS_CACHE.get(clazz);
        ClassInfo info = new ClassInfo(clazz);
        CLASS_CACHE.put(clazz, info);
        CLASS_NAME_CACHE.put(info.getName(), info);
        return info;
    }

    private final Class<?> clazzRaw;
    private final String name;
    private final int modifiers;
    private final boolean isInterface;
    private final List<ITypeInfo> parameters;
    private List<MethodInfo> methodInfo;
    private List<FieldInfo> fieldInfo;
    private List<ConstructorInfo> constructorInfo;
    private final ClassInfo superClass;
    private final List<ClassInfo> interfaces;
    private final List<Annotation> annotations;

    private ClassInfo(Class<?> clazz) {
        clazzRaw = clazz;
        name = MethodInfo.getMappedOrDefaultClass(clazzRaw);
        modifiers = clazzRaw.getModifiers();
        isInterface = clazzRaw.isInterface();
        superClass = (clazzRaw.getSuperclass() == Object.class || clazzRaw.getSuperclass() == null) ? null : getOrCache(clazzRaw.getSuperclass());
        interfaces = Arrays.stream(clazzRaw.getInterfaces()).map(ClassInfo::getOrCache).collect(Collectors.toList());
        parameters = Arrays.stream(clazzRaw.getTypeParameters()).map(InfoTypeResolver::resolveType).collect(Collectors.toList());
        annotations = new ArrayList<>();
        annotations.addAll(List.of(clazz.getAnnotations()));

        if (clazz.isAnnotationPresent(HideFromJS.class)) {
            methodInfo = List.of();
            fieldInfo = List.of();
            constructorInfo = List.of();
            return;
        }

        try {
            JavaMembers members = JavaMembers.lookupClass(ProbeCommands.CONTEXT_DATA, clazz, clazz, false);
            constructorInfo = members.getAccessibleConstructors().stream().map(ConstructorInfo::new).collect(Collectors.toList());
            methodInfo = members.getAccessibleMethods(false).stream()
                    .filter(m -> m.method.getDeclaringClass() == clazz || m.method.isDefault())
                    .map(m -> new MethodInfo(m, clazz))
                    .collect(Collectors.toList());
            fieldInfo = members.getAccessibleFields(false)
                    .stream()
                    .filter(f -> f.field.getDeclaringClass() == clazz)
                    .map(FieldInfo::new)
                    .collect(Collectors.toList());
        } catch (Throwable e) {
            ProbeJS.LOGGER.warn("Error occured when resolving class %s! Touching the class with KubeJS will likely to crash too!".formatted(clazz.getName()));
            constructorInfo = List.of();
            methodInfo = List.of();
            fieldInfo = List.of();
            NameResolver.putResolvedName(clazz, new NameResolver.ResolvedName(List.of("any")));
        }
    }


    public boolean isInterface() {
        return isInterface;
    }

    public boolean isAbstract() {
        return Modifier.isAbstract(modifiers);
    }

    public ClassInfo getSuperClass() {
        return superClass;
    }

    public List<ClassInfo> getInterfaces() {
        return interfaces;
    }

    public List<FieldInfo> getFieldInfo() {
        return fieldInfo;
    }

    public List<ConstructorInfo> getConstructorInfo() {
        return constructorInfo;
    }


    public List<MethodInfo> getMethodInfo() {
        return methodInfo;
    }

    public List<ITypeInfo> getParameters() {
        return parameters;
    }

    public boolean isEnum() {
        return clazzRaw.isEnum();
    }

    public Class<?> getClazzRaw() {
        return clazzRaw;
    }

    public String getName() {
        return name;
    }

    public ITypeInfo getSuperClassType() {
        return InfoTypeResolver.resolveType(clazzRaw.getGenericSuperclass());
    }

    public List<ITypeInfo> getInterfaceTypes() {
        return Arrays.stream(clazzRaw.getGenericInterfaces()).map(InfoTypeResolver::resolveType).collect(Collectors.toList());
    }

    public List<Annotation> getAnnotations() {
        return annotations;
    }
}
