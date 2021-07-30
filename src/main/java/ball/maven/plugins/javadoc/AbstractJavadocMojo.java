package ball.maven.plugins.javadoc;

import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;

import static lombok.AccessLevel.PROTECTED;

/**
 * Abstract base class for javadoc {@link org.apache.maven.plugin.Mojo}s.
 *
 * {@bean.info}
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 * @version $Revision$
 */
@NoArgsConstructor(access = PROTECTED) @Getter @ToString @Slf4j
public abstract class AbstractJavadocMojo extends AbstractMojo {
    @Parameter( defaultValue = "${project.remoteArtifactRepositories}", readonly = true, required = true )
    private List<ArtifactRepository> remoteRepositories = Collections.emptyList();

    @Parameter(defaultValue = "false", property = "javadoc.skip")
    private boolean skip = false;

    public void execute() throws MojoExecutionException, MojoFailureException {
    }
}
