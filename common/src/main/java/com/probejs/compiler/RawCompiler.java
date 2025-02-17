package com.probejs.compiler;

import com.probejs.ProbePaths;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;

public class RawCompiler {
    public static void compileRaw() throws IOException {
        BufferedWriter writer = Files.newBufferedWriter(ProbePaths.GENERATED.resolve("raw.d.ts"));
        writer.write("""
                interface String {
                     readonly namespace: string,
                     readonly path: string
                }
                """);
        writer.flush();
    }
}
