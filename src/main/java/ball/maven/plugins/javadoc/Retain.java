package ball.maven.plugins.javadoc;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PACKAGE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * {@link java.lang.annotation.Annotation} to prevent {@code javac} from
 * discarding {@code package-info.class}.
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 * @version $Revision$
 */
@Retention(RUNTIME)
@Target({ PACKAGE })
public @interface Retain {
}
