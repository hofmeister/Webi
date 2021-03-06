package com.vonhof.webi.annotation;

import com.vonhof.webi.HttpMethod;
import java.lang.annotation.*;

/**
 * Use to force a specific url for a given method or type
 * @author Henrik Hofmeister <@vonhofdk>
 */

@Documented
@Target(value={ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface Path {
    String value() default "";
    HttpMethod method() default HttpMethod.GET;
}
