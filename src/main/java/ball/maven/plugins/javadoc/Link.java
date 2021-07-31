package ball.maven.plugins.javadoc;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import lombok.Data;
import lombok.Getter;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.shared.artifact.filter.StrictPatternIncludesArtifactFilter;

/**
 * {@code <link/>} parameter.
 *
 * {@bean.info}
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 * @version $Revision$
 */
@Data
public class Link {
    private String artifact = null;
    private URL url = null;
    @Getter(lazy = true)
    private final StrictPatternIncludesArtifactFilter filter =
        new StrictPatternIncludesArtifactFilter(Arrays.asList(artifact.split("[,\\p{Space}]+")));

    /**
     * See {@link StrictPatternIncludesArtifactFilter#include(Artifact)}.
     */
    public boolean include(Artifact artifact) {
        return getFilter().include(artifact);
    }

    /**
     * Method to return a {@link URL} substituting <code>{g}</code>,
     * <code>{a}</code>, and <code>{v}</code> with
     * {@link Artifact#getGroupId()}, {@link Artifact#getArtifactId()}, and
     * {@link Artifact#getVersion()}, respectively.
     *
     * @param   artifact        The {@link Artifact}.
     *
     * @return  The {@link URL} after substitution.
     */
    public URL getUrl(Artifact artifact) {
        URL url = getUrl();

        if (url != null && artifact != null) {
            try {
                url =
                    new URL(url.toString()
                            .replaceAll("(?i)[{]g[}]", artifact.getGroupId())
                            .replaceAll("(?i)[{]a[}]", artifact.getArtifactId())
                            .replaceAll("(?i)[{]v[}]", artifact.getVersion()));
            } catch (MalformedURLException exception) {
                throw new IllegalArgumentException(exception);
            }
        }

        return url;
    }
}
