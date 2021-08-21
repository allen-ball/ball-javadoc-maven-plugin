package ball.maven.plugins.javadoc;
/*-
 * ##########################################################################
 * Javadoc Maven Plugin
 * $Id$
 * $HeadURL$
 * %%
 * Copyright (C) 2021 Allen D. Ball
 * %%
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
 * ##########################################################################
 */
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.inject.Inject;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static org.apache.maven.plugins.annotations.ResolutionScope.RUNTIME;

/**
 * {@link org.apache.maven.plugin.Mojo} to generate offline javadoc map.
 * For each documented package, generate the following key/value pairs:
 * <i>package</i>/Javadoc Root URL, <i>package</i>-module / Module (e.g.,
 * java.base), and <i>package</i>-artifact / groupId:artifactId.
 *
 * The <i>package</i>-module and <i>package</i>-artifact key/values are
 * absent if no module or artifact respectively are specified.
 *
 * {@maven.plugin.fields}
 *
 * {@bean.info}
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 * @version $Revision$
 */
@Mojo(name = "generate-javadoc-map", requiresDependencyResolution = RUNTIME, requiresProject = true)
@NoArgsConstructor @ToString @Slf4j
public class GenerateJavadocMapMojo extends AbstractJavadocMojo {
    private static final String ELEMENT_LIST = "element-list";
    private static final String PACKAGE_LIST = "package-list";

    private static final List<String> NAMES = Arrays.asList(ELEMENT_LIST, PACKAGE_LIST);

    private static final String MODULE_PREFIX = "module:";

    @Parameter(property = "outputDirectory", defaultValue = "${project.build.directory}")
    private File outputDirectory = null;

    @Parameter(property = "outputFileName", defaultValue = "javadoc-map.properties")
    private String outputFileName = null;

    @Parameter(defaultValue = "true", property = "includeDependencyManagement")
    private boolean includeDependencyManagement = true;

    @Inject private MavenProject project = null;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        super.execute();

        try {
            if (! isSkip()) {
                Properties properties = new Properties();
                Map<Artifact,URL> map = getResolvedOfflinelinkMap(project, includeDependencyManagement);

                map.forEach((k, v) -> load(properties, k, v));

                Set<URL> set = getLinkSet(project, includeDependencyManagement);

                set.removeAll(map.values());
                set.forEach(t -> load(properties, null, t));

                Path path = outputDirectory.toPath().resolve(outputFileName);

                Files.createDirectories(path.getParent());

                try (OutputStream out = Files.newOutputStream(path)) {
                    String name = path.getFileName().toString();

                    if (name.toLowerCase().endsWith(".xml")) {
                        properties.storeToXML(out, name);
                    } else {
                        properties.store(out, name);
                    }
                }
            } else {
                log.info("Skipping javadoc map generation.");
            }
        } catch (Throwable throwable) {
            log.error("{}", throwable.getMessage(), throwable);

            if (throwable instanceof MojoExecutionException) {
                throw (MojoExecutionException) throwable;
            } else if (throwable instanceof MojoFailureException) {
                throw (MojoFailureException) throwable;
            } else {
                throw new MojoExecutionException(throwable.getMessage(), throwable);
            }
        }
    }

    private void load(Properties properties, Artifact artifact, URL javadoc) {
        URL location = (artifact != null) ? toURL(artifact) : javadoc;
        List<String> lines = null;

        for (String name : NAMES) {
            try {
                URL url = new URL(location + name);

                try (InputStream in = url.openStream()) {
                    lines =
                        new BufferedReader(new InputStreamReader(in, UTF_8)).lines()
                        .collect(toList());
                }

                break;
            } catch (Exception exception) {
                continue;
            }
        }

        if (lines != null) {
            String module = null;

            for (String line : lines) {
                if (line.startsWith(MODULE_PREFIX)) {
                    module = line.substring(MODULE_PREFIX.length());
                } else {
                    if (! properties.containsKey(line)) {
                        properties.put(line, javadoc.toString());

                        if (module != null) {
                            properties.put(line + "-module", module);
                        }

                        if (artifact != null) {
                            properties.put(line + "-artifact", ArtifactUtils.versionlessKey(artifact));
                        }
                    }
                }
            }
        } else {
            log.warn("Could not read any of {} from {}", NAMES, location);
        }
    }

    private URL toURL(Artifact artifact) {
        URL url = null;

        try {
            url =
                new URI("jar", artifact.getFile().toURI().toASCIIString() + "!/", null)
                .toURL();
        } catch(URISyntaxException | MalformedURLException exception) {
            log.debug("{}: {}", artifact, exception.getMessage(), exception);
            throw new IllegalStateException(exception);
        }

        return url;
    }
}
