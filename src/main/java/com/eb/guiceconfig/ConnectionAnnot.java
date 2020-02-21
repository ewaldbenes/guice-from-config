package com.eb.guiceconfig;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;

import com.google.inject.BindingAnnotation;

@Retention(RUNTIME) @BindingAnnotation
@interface ConnectionAnnot {
    int num();
}
