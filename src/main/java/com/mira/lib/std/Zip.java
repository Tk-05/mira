package com.mira.lib.std;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import com.mira.lib.Lib;
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.values.BytesValue;
import com.mira.runtime.values.NullValue;

public class Zip implements Lib {

    private static BytesValue toBytes(Object arg) {
        if (arg instanceof BytesValue bv) {
            return bv;
        }
        throw new RuntimeException("Expected bytes, got: " + arg.getClass().getSimpleName());
    }

    private static List<String> toStringList(Object arg) {
        List<Expression> members = switch (arg) {
            case ListExpression l ->
                l.getMembers();
            default ->
                throw new RuntimeException("Expected list");
        };
        List<String> out = new ArrayList<>(members.size());
        for (Expression e : members) {
            out.add(String.valueOf(e));
        }
        return out;
    }

    @Override
    public void loadLib(Environment environment) {

        environment.define("gzipCompress", new NativeFunction(1, "bytes", args -> {
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                try (GZIPOutputStream gz = new GZIPOutputStream(bos)) {
                    gz.write(toBytes(args.get(0)).getData());
                }
                return new BytesValue(bos.toByteArray());
            } catch (IOException e) {
                throw new RuntimeException("gzipCompress failed: " + e.getMessage());
            }
        }));

        environment.define("gzipDecompress", new NativeFunction(1, "bytes", args -> {
            try {
                ByteArrayInputStream bis = new ByteArrayInputStream(toBytes(args.get(0)).getData());
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                try (GZIPInputStream gz = new GZIPInputStream(bis)) {
                    gz.transferTo(bos);
                }
                return new BytesValue(bos.toByteArray());
            } catch (IOException e) {
                throw new RuntimeException("gzipDecompress failed: " + e.getMessage());
            }
        }));

        environment.define("deflate", new NativeFunction(1, "bytes", args -> {
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                try (DeflaterOutputStream dos = new DeflaterOutputStream(bos, new Deflater())) {
                    dos.write(toBytes(args.get(0)).getData());
                }
                return new BytesValue(bos.toByteArray());
            } catch (IOException e) {
                throw new RuntimeException("deflate failed: " + e.getMessage());
            }
        }));

        environment.define("inflate", new NativeFunction(1, "bytes", args -> {
            try {
                ByteArrayInputStream bis = new ByteArrayInputStream(toBytes(args.get(0)).getData());
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                try (InflaterInputStream iis = new InflaterInputStream(bis)) {
                    iis.transferTo(bos);
                }
                return new BytesValue(bos.toByteArray());
            } catch (IOException e) {
                throw new RuntimeException("inflate failed: " + e.getMessage());
            }
        }));

        environment.define("createZip", new NativeFunction(2, "outputPath, paths", args -> {
            try {
                Path outputPath = Path.of(String.valueOf(args.get(0)));
                List<String> filePaths = toStringList(args.get(1));
                if (outputPath.getParent() != null) {
                    Files.createDirectories(outputPath.getParent());
                }
                try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(outputPath))) {
                    for (String filePath : filePaths) {
                        Path fp = Path.of(filePath);
                        zos.putNextEntry(new ZipEntry(fp.getFileName().toString()));
                        Files.copy(fp, zos);
                        zos.closeEntry();
                    }
                }
                return NullValue.INSTANCE;
            } catch (IOException e) {
                throw new RuntimeException("createZip failed: " + e.getMessage());
            }
        }));

        environment.define("extractZip", new NativeFunction(2, "zipPath, outputDir", args -> {
            try {
                Path zipPath = Path.of(String.valueOf(args.get(0)));
                Path outputDir = Path.of(String.valueOf(args.get(1)));
                Files.createDirectories(outputDir);
                try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        Path target = outputDir.resolve(entry.getName());
                        if (entry.isDirectory()) {
                            Files.createDirectories(target);
                        } else {
                            if (target.getParent() != null) {
                                Files.createDirectories(target.getParent());
                            }
                            Files.copy(zis, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        }
                        zis.closeEntry();
                    }
                }
                return NullValue.INSTANCE;
            } catch (IOException e) {
                throw new RuntimeException("extractZip failed: " + e.getMessage());
            }
        }));
    }
}
