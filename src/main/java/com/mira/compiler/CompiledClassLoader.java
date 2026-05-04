package com.mira.compiler;

import java.util.Map;

public class CompiledClassLoader extends ClassLoader {

    private final Map<String, byte[]> classes;

    public CompiledClassLoader(Map<String, byte[]> classes) {
        super(CompiledClassLoader.class.getClassLoader());
        this.classes = classes;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytes = classes.get(name.replace('.', '/'));
        if (bytes == null) {
            throw new ClassNotFoundException(name);
        }
        return defineClass(name, bytes, 0, bytes.length);
    }
}
