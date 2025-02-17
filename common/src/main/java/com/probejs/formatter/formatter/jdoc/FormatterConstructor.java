package com.probejs.formatter.formatter.jdoc;

import com.probejs.formatter.formatter.IFormatter;
import com.probejs.jdoc.document.DocumentConstructor;
import com.probejs.util.Util;

import java.util.List;
import java.util.stream.Collectors;

public class FormatterConstructor extends DocumentFormatter<DocumentConstructor> {
    public FormatterConstructor(DocumentConstructor document) {
        super(document);
    }

    @Override
    public List<String> formatDocument(Integer indent, Integer stepIndent) {
        return List.of(Util.indent(indent) + "constructor(%s)".formatted(
                document.getParams()
                        .stream()
                        .map(FormatterMethod.FormatterParam::new)
                        .map(FormatterMethod.FormatterParam::underscored)
                        .map(IFormatter::formatFirst)
                        .collect(Collectors.joining(", "))
        ));
    }
}
