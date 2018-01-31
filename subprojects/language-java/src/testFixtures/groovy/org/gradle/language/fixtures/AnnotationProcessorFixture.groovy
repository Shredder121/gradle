/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.language.fixtures

import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDetector
import org.gradle.api.internal.tasks.compile.processing.IncrementalAnnotationProcessorType
import org.gradle.test.fixtures.file.TestFile

/**
 * Generates the source for an annotation processor that handles a `@Helper` annotation.
 * For those classes with the `@Helper` annotation the processor generates a <class-name>Helper class.
 * The annotation processor requires a support library, the source for which is also generated by this fixture.
 */
class AnnotationProcessorFixture {
    String message = "greetings"
    IncrementalAnnotationProcessorType type
    private String suffix = ""

    void setSuffix(String suffix) {
        this.suffix = suffix ? " " + suffix : ""
    }

    def writeApiTo(TestFile projectDir) {
        // Annotation handled by processor
        projectDir.file('src/main/java/Helper.java').text = '''
            public @interface Helper {
            }
'''
    }

    def writeSupportLibraryTo(TestFile projectDir) {
        // Some library class used by processor at runtime
        def utilClass = projectDir.file('src/main/java/Util.java')
        utilClass.text = """
            class Util {
                static String getValue() { return "${message}"; }
            }
"""
    }

    def writeAnnotationProcessorTo(TestFile projectDir) {
        // The annotation processor
        projectDir.file('src/main/java/Processor.java').text = """
            import javax.annotation.processing.AbstractProcessor;
            import java.util.Set;
            import java.util.Collections;
            import java.io.Writer;
            import javax.lang.model.SourceVersion;
            import javax.lang.model.util.Elements;
            import javax.annotation.processing.Filer;
            import javax.annotation.processing.Messager;
            import javax.lang.model.element.Element;
            import javax.lang.model.element.TypeElement;
            import javax.tools.JavaFileObject;
            import javax.annotation.processing.ProcessingEnvironment;
            import javax.annotation.processing.RoundEnvironment;
            import javax.annotation.processing.SupportedOptions;
            import javax.tools.Diagnostic;
                                       
            @SupportedOptions({ "message" })
            public class Processor extends AbstractProcessor {
                private Elements elementUtils;
                private Filer filer;
                private Messager messager;
                private String messageFromOptions;
    
                @Override
                public Set<String> getSupportedAnnotationTypes() {
                    return Collections.singleton(Helper.class.getName());
                }
            
                @Override
                public SourceVersion getSupportedSourceVersion() {
                    return SourceVersion.latestSupported();
                }
    
                @Override
                public synchronized void init(ProcessingEnvironment processingEnv) {
                    elementUtils = processingEnv.getElementUtils();
                    filer = processingEnv.getFiler();
                    messager = processingEnv.getMessager();
                    messageFromOptions = processingEnv.getOptions().get("message");
                }
    
                @Override
                public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
                    for (TypeElement annotation : annotations) {
                        if (annotation.getQualifiedName().toString().equals(Helper.class.getName())) {
                            for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                                TypeElement typeElement = (TypeElement) element;
                                String helperName = typeElement.getSimpleName().toString() + "Helper";
                                try {
                                    JavaFileObject sourceFile = filer.createSourceFile(helperName, element);
                                    Writer writer = sourceFile.openWriter();
                                    try {
                                        writer.write("class " + helperName + " {");
                                        writer.write("    String getValue() { return \\"");
                                        if (messageFromOptions == null) {
                                            writer.write(Util.getValue() + "${suffix}");
                                        } else {
                                            writer.write(messageFromOptions);
                                        }
                                        writer.write("\\"; }");
                                        writer.write("}");
                                    } finally {
                                        writer.close();
                                    }
                                } catch (Exception e) {
                                    messager.printMessage(Diagnostic.Kind.ERROR, "Failed to generate source file " + helperName, element);
                                }
                            }
                        }
                    }
                    return true;
                }
            }
"""
        projectDir.file("src/main/resources/$AnnotationProcessorDetector.PROCESSOR_DECLARATION").text = 'Processor'
        if (type) {
            projectDir.file("src/main/resources/$AnnotationProcessorDetector.INCREMENTAL_PROCESSOR_DECLARATION").text = "Processor=$type"
        }
    }
}
