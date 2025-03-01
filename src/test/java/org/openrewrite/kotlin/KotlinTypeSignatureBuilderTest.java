/*
 * Copyright 2021 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.kotlin;

import org.jetbrains.kotlin.com.intellij.openapi.Disposable;
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer;
import org.jetbrains.kotlin.fir.FirSession;
import org.jetbrains.kotlin.fir.declarations.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Parser;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.tree.ParsingExecutionContextView;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("ConstantConditions")
public class KotlinTypeSignatureBuilderTest {
    private static final String goat = StringUtils.readFully(KotlinTypeSignatureBuilderTest.class.getResourceAsStream("/KotlinTypeGoat.kt"));

    private static Disposable disposable = Disposer.newDisposable();
    private static Map<FirSession, List<CompiledKotlinSource>> cu;

    @BeforeAll
    public static void beforeAll() {
        disposable = Disposer.newDisposable();
        cu = KotlinParser.builder()
          .logCompilationWarningsAndErrors(true)
          .build()
          .parseInputsToCompilerAst(
            disposable,
            singletonList(new Parser.Input(Paths.get("KotlinTypeGoat.kt"), () -> new ByteArrayInputStream(goat.getBytes(StandardCharsets.UTF_8)))),
            null,
            new ParsingExecutionContextView(new InMemoryExecutionContext(Throwable::printStackTrace)));
    }

    @AfterAll
    public static void afterAll() {
        Disposer.dispose(disposable);
    }

    public KotlinTypeSignatureBuilder signatureBuilder() {
        return new KotlinTypeSignatureBuilder(cu.keySet().iterator().next());
    }

    private FirFile getCompiledSource() {
        return cu.values().iterator().next().get(0).getFirFile();
    }

    public String constructorSignature() {
        return signatureBuilder().methodDeclarationSignature(getCompiledSource().getDeclarations().stream()
          .map(FirRegularClass.class::cast)
          .flatMap(it -> it.getDeclarations().stream())
          .filter(FirConstructor.class::isInstance)
          .map(FirFunction.class::cast)
          .findFirst()
          .orElseThrow()
          .getSymbol());
    }

    public Object innerClassSignature(String innerClassSimpleName) {
        return signatureBuilder().signature(getCompiledSource().getDeclarations().stream()
                .map(FirRegularClass.class::cast)
                .flatMap(it -> it.getDeclarations().stream())
                .filter(FirRegularClass.class::isInstance)
                .map(FirRegularClass.class::cast)
                .filter(it -> innerClassSimpleName.equals(it.getName().asString()))
                .findFirst()
                .orElseThrow()
                .getSymbol());
    }

    public String fieldSignature(String field) {
        return signatureBuilder().variableSignature(getCompiledSource().getDeclarations().stream()
                .map(FirRegularClass.class::cast)
                .flatMap(it -> it.getDeclarations().stream())
                .filter(FirProperty.class::isInstance)
                .map(FirProperty.class::cast)
                .filter(it -> field.equals(it.getName().asString()))
                .findFirst()
                .orElseThrow()
                .getSymbol(), null);
    }

    public Object firstMethodParameterSignature(String methodName) {
        return signatureBuilder().signature(getCompiledSource().getDeclarations().stream()
                .map(FirRegularClass.class::cast)
                .flatMap(it -> it.getDeclarations().stream())
                .filter(FirSimpleFunction.class::isInstance)
                .map(FirSimpleFunction.class::cast)
                .filter(it -> methodName.equals(it.getName().asString()))
                .findFirst()
                .orElseThrow()
                .getValueParameters()
                .get(0)
                .getReturnTypeRef());
    }

    public Object lastClassTypeParameter() {
        return signatureBuilder().signature(getCompiledSource().getDeclarations().stream()
                .map(FirRegularClass.class::cast)
                .findFirst()
                .orElseThrow()
                .getTypeParameters()
                .get(1));
    }

    public String methodSignature(String methodName) {
        return signatureBuilder().methodDeclarationSignature(getCompiledSource().getDeclarations().stream()
                .map(FirRegularClass.class::cast)
                .flatMap(it -> it.getDeclarations().stream())
                .filter(FirSimpleFunction.class::isInstance)
                .map(FirSimpleFunction.class::cast)
                .filter(it -> methodName.equals(it.getName().asString()))
                .findFirst()
                .orElseThrow()
                .getSymbol());
    }

    @Test
    public void constructor() {
        assertThat(constructorSignature())
                .isEqualTo("org.openrewrite.kotlin.KotlinTypeGoat{name=<constructor>,return=org.openrewrite.kotlin.KotlinTypeGoat,parameters=[]}");
    }

    @Test
    public void parameterizedField() {
        assertThat(fieldSignature("parameterizedField"))
                .isEqualTo("org.openrewrite.kotlin.KotlinTypeGoat{name=parameterizedField,type=org.openrewrite.kotlin.PT<org.openrewrite.kotlin.KotlinTypeGoat$TypeA>}");
    }

    @Test
    public void classSignature() {
        assertThat(firstMethodParameterSignature("clazz"))
                .isEqualTo("org.openrewrite.kotlin.C");
        assertThat(methodSignature("clazz"))
                .isEqualTo("org.openrewrite.kotlin.KotlinTypeGoat{name=clazz,return=kotlin.Unit,parameters=[org.openrewrite.kotlin.C]}");
    }

    @Test
    public void parameterized() {
        assertThat(firstMethodParameterSignature("parameterized"))
                .isEqualTo("org.openrewrite.kotlin.PT<org.openrewrite.kotlin.C>");
        assertThat(methodSignature("parameterized"))
                .isEqualTo("org.openrewrite.kotlin.KotlinTypeGoat{name=parameterized,return=org.openrewrite.kotlin.PT<org.openrewrite.kotlin.C>,parameters=[org.openrewrite.kotlin.PT<org.openrewrite.kotlin.C>]}");
    }

    @Test
    public void parameterizedRecursive() {
        assertThat(firstMethodParameterSignature("parameterizedRecursive"))
                .isEqualTo("org.openrewrite.kotlin.PT<org.openrewrite.kotlin.PT<org.openrewrite.kotlin.C>>");
        assertThat(methodSignature("parameterizedRecursive"))
                .isEqualTo("org.openrewrite.kotlin.KotlinTypeGoat{name=parameterizedRecursive,return=org.openrewrite.kotlin.PT<org.openrewrite.kotlin.PT<org.openrewrite.kotlin.C>>,parameters=[org.openrewrite.kotlin.PT<org.openrewrite.kotlin.PT<org.openrewrite.kotlin.C>>]}");
    }

    @Test
    public void generic() {
        assertThat(firstMethodParameterSignature("generic"))
                .isEqualTo("org.openrewrite.kotlin.PT<Generic{out org.openrewrite.kotlin.C}>");
        assertThat(methodSignature("generic"))
                .isEqualTo("org.openrewrite.kotlin.KotlinTypeGoat{name=generic,return=org.openrewrite.kotlin.PT<Generic{out org.openrewrite.kotlin.C}>,parameters=[org.openrewrite.kotlin.PT<Generic{out org.openrewrite.kotlin.C}>]}");
    }

    @Test
    public void genericT() {
        assertThat(firstMethodParameterSignature("genericT"))
                .isEqualTo("Generic{T}");
        assertThat(methodSignature("genericT"))
                .isEqualTo("org.openrewrite.kotlin.KotlinTypeGoat{name=genericT,return=Generic{T},parameters=[Generic{T}]}");
    }

    @Test
    public void genericContravariant() {
        assertThat(firstMethodParameterSignature("genericContravariant"))
                .isEqualTo("org.openrewrite.kotlin.PT<Generic{in org.openrewrite.kotlin.C}>");
        assertThat(methodSignature("genericContravariant"))
                .isEqualTo("org.openrewrite.kotlin.KotlinTypeGoat{name=genericContravariant,return=org.openrewrite.kotlin.PT<Generic{in org.openrewrite.kotlin.C}>,parameters=[org.openrewrite.kotlin.PT<Generic{in org.openrewrite.kotlin.C}>]}");
    }

    @Test
    public void genericUnbounded() {
        assertThat(firstMethodParameterSignature("genericUnbounded"))
                .isEqualTo("org.openrewrite.kotlin.PT<Generic{U}>");
        assertThat(methodSignature("genericUnbounded"))
                .isEqualTo("org.openrewrite.kotlin.KotlinTypeGoat{name=genericUnbounded,return=org.openrewrite.kotlin.PT<Generic{U}>,parameters=[org.openrewrite.kotlin.PT<Generic{U}>]}");
    }

    @Test
    public void innerClass() {
        assertThat(firstMethodParameterSignature("inner"))
                .isEqualTo("org.openrewrite.kotlin.C$Inner");
        assertThat(methodSignature("inner"))
                .isEqualTo("org.openrewrite.kotlin.KotlinTypeGoat{name=inner,return=kotlin.Unit,parameters=[org.openrewrite.kotlin.C$Inner]}");
    }

    @Disabled("Requires parsing intersection types")
    @Test
    public void inheritedJavaTypeGoat() {
        assertThat(firstMethodParameterSignature("inheritedJavaTypeGoat"))
                .isEqualTo("org.openrewrite.java.JavaTypeGoat$InheritedJavaTypeGoat<Generic{T}, Generic{U extends org.openrewrite.java.PT<Generic{U}> & org.openrewrite.java.C}>");
        assertThat(methodSignature("inheritedJavaTypeGoat"))
                .isEqualTo("org.openrewrite.java.JavaTypeGoat{name=inheritedJavaTypeGoat,return=org.openrewrite.java.JavaTypeGoat$InheritedJavaTypeGoat<Generic{T}, Generic{U extends org.openrewrite.java.PT<Generic{U}> & org.openrewrite.java.C}>,parameters=[org.openrewrite.java.JavaTypeGoat$InheritedJavaTypeGoat<Generic{T}, Generic{U extends org.openrewrite.java.PT<Generic{U}> & org.openrewrite.java.C}>]}");
    }

    @Disabled("Requires reference of type params from parent class")
    @Test
    public void extendsJavaTypeGoat() {
        assertThat(innerClassSignature("ExtendsKotlinTypeGoat"))
                .isEqualTo("org.openrewrite.kotlin.KotlinTypeGoat$ExtendsKotlinTypeGoat");
    }

    @Disabled("Requires parsing intersection types")
    @Test
    public void genericIntersection() {
        assertThat(firstMethodParameterSignature("genericIntersection"))
                .isEqualTo("Generic{U extends org.openrewrite.java.JavaTypeGoat$TypeA & org.openrewrite.java.PT<Generic{U}> & org.openrewrite.java.C}");
        assertThat(methodSignature("genericIntersection"))
                .isEqualTo("org.openrewrite.java.JavaTypeGoat{name=genericIntersection,return=Generic{U extends org.openrewrite.java.JavaTypeGoat$TypeA & org.openrewrite.java.PT<Generic{U}> & org.openrewrite.java.C},parameters=[Generic{U extends org.openrewrite.java.JavaTypeGoat$TypeA & org.openrewrite.java.PT<Generic{U}> & org.openrewrite.java.C}]}");
    }

    @Disabled("Requires parsing intersection types")
    @Test
    public void recursiveIntersection() {
        assertThat(firstMethodParameterSignature("recursiveIntersection"))
                .isEqualTo("Generic{U extends org.openrewrite.java.JavaTypeGoat$Extension<Generic{U}> & org.openrewrite.java.Intersection<Generic{U}>}");
        assertThat(methodSignature("recursiveIntersection"))
                .isEqualTo("org.openrewrite.java.JavaTypeGoat{name=recursiveIntersection,return=void,parameters=[Generic{U extends org.openrewrite.java.JavaTypeGoat$Extension<Generic{U}> & org.openrewrite.java.Intersection<Generic{U}>}]}");
    }

    @Disabled
    @Test
    public void genericRecursiveInClassDefinition() {
        assertThat(lastClassTypeParameter())
                .isEqualTo("Generic{S in org.openrewrite.kotlin.PT<Generic{S}> & org.openrewrite.kotlin.C}");
    }

    @Disabled
    @Test
    public void genericRecursiveInMethodDeclaration() {
        // <U : KotlinTypeGoat<U, *>> genericRecursive(n: KotlinTypeGoat<out Array<U>, *>): KotlinTypeGoat<out Array<U>, *>
        assertThat(firstMethodParameterSignature("genericRecursive"))
                .isEqualTo("org.openrewrite.kotlin.KotlinTypeGoat<Generic{ extends kotlin.Array<Generic{U}>, Generic{*}>");
        assertThat(methodSignature("genericRecursive"))
                .isEqualTo("org.openrewrite.kotlin.KotlinTypeGoat{name=genericRecursive,return=org.openrewrite.kotlin.KotlinTypeGoat<Generic{? extends Generic{U extends org.openrewrite.kotlin.KotlinTypeGoat<Generic{U}, Generic{?}>}[]}, Generic{?}>,parameters=[org.openrewrite.kotlin.KotlinTypeGoat<Generic{? extends Generic{U extends org.openrewrite.kotlin.KotlinTypeGoat<Generic{U}, Generic{?}>}[]}, Generic{?}>]}");
    }
}
