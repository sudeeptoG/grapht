/*
 * Grapht, an open source dependency injector.
 * Copyright 2014-2015 various contributors (see CONTRIBUTORS.txt)
 * Copyright 2010-2014 Regents of the University of Minnesota
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.grouplens.grapht.annotation;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.inject.Qualifier;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Annotation processor that checks and validates DI annotations.
 */
public class AnnotationValidator extends AbstractProcessor {
    private static final String DEFAULT_ANNOT_PREFIX = "org.grouplens.grapht.annotation.Default";
    private static final Pattern DEFAULT_PATTERN = Pattern.compile("^" + Pattern.quote(DEFAULT_ANNOT_PREFIX));

    @Override
    public SourceVersion getSupportedSourceVersion() {
        // support version 6 or 7
        // we can't compile against RELEASE_7 and maintain Java 6 compatibility, but the
        // processor is Java 7-compatible. We have not tested against Java 8, however.
        SourceVersion[] versions = SourceVersion.values();
        SourceVersion v6 = SourceVersion.RELEASE_6;
        assert v6.ordinal() < versions.length;
        // we support up through Java 8
        return versions[Math.min(v6.ordinal() + 2, versions.length - 1)];
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> atypes = new HashSet<String>();
        atypes.add(Qualifier.class.getName());
        atypes.add(Attribute.class.getName());
        atypes.add(AliasFor.class.getName());
        return atypes;
    }

    private void warning(Element e, String fmt, Object... args) {
        Messager log = processingEnv.getMessager();
        String msg = String.format(fmt, args);
        log.printMessage(Diagnostic.Kind.MANDATORY_WARNING, msg, e);
    }

    private void error(Element e, String fmt, Object... args) {
        Messager log = processingEnv.getMessager();
        String msg = String.format(fmt, args);
        log.printMessage(Diagnostic.Kind.ERROR, msg, e);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        analyzeQualifiers(roundEnv);
        analyzeAttributes(roundEnv);
        analyzeAliases(roundEnv);
        return false; // let other processors work too
    }

    private void analyzeQualifiers(RoundEnvironment re) {
      analyzeAnnotations(re, Qualifier.class, "qualifier");
    }

    private void analyzeAttributes(RoundEnvironment re) {
      analyzeAnnotations(re, Attribute.class, "attribute");
    }

    private void analyzeAnnotations(RoundEnvironment re, Class<? extends Annotation> annotation, String annotationName){
      Set<? extends Element> elts = re.getElementsAnnotatedWith(annotation);
      for (Element elt: elts) {
          if (elt.getAnnotation(Documented.class) == null) {
              warning(elt, String.format("%s annotation should be @Documented", annotationName));
          }
          Retention ret = elt.getAnnotation(Retention.class);
          if (ret == null || !ret.value().equals(RetentionPolicy.RUNTIME)) {
              error(elt, String.format("%s annotation must have RUNTIME retention", annotationName));
          }
      }
    }

    private void analyzeAliases(RoundEnvironment re) {
        Set<? extends Element> elts = re.getElementsAnnotatedWith(AliasFor.class);
        for (Element elt : elts) {
            if (elt.getAnnotation(Qualifier.class) == null) {
                error(elt, "alias annotation must also be a qualifier");
            }
            if (elt.getAnnotation(AllowUnqualifiedMatch.class) != null) {
                error(elt, "alias annotation has @AllowUnqualifiedMatch (should be on alias target)");
            }
            if (elt.getAnnotation(AllowDefaultMatch.class) != null) {
                error(elt, "alias annotation has @AllowDefaultMatch (should be on alias target)");
            }
            for (AnnotationMirror mirror: elt.getAnnotationMirrors()) {
                Element element = mirror.getAnnotationType().asElement();
                if (element instanceof TypeElement) {
                    TypeElement type = (TypeElement) element;
                    Name name = type.getQualifiedName();
                    if (DEFAULT_PATTERN.matcher(name).matches()) {
                        warning(elt, "alias annotation has %s, defaults should be on alias target", type.getSimpleName());
                    }
                }
            }
        }
    }
}
