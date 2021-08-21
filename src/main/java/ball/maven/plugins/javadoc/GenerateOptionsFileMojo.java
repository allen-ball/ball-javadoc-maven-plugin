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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
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

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static org.apache.maven.plugins.annotations.LifecyclePhase.GENERATE_SOURCES;
import static org.apache.maven.plugins.annotations.ResolutionScope.RUNTIME;

/**
 * {@link org.apache.maven.plugin.Mojo} to generate javadoc options file for
 * {@code maven-javadoc-plugin}.
 *
 * {@maven.plugin.fields}
 *
 * {@bean.info}
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 * @version $Revision$
 */
@Mojo(name = "generate-options-file",
      requiresDependencyResolution = RUNTIME,
      defaultPhase = GENERATE_SOURCES, requiresProject = true)
@NoArgsConstructor @ToString @Slf4j
public class GenerateOptionsFileMojo extends AbstractJavadocMojo {
    private static final Pattern JAR_ENTRY_PATTERN = Pattern.compile("^(package|element)[^-]*-list$");

    @Parameter(property = "outputDirectory", defaultValue = "${project.build.directory}/javadoc-options")
    private File outputDirectory = null;

    @Parameter(defaultValue = "false", property = "includeDependencyManagement")
    private boolean includeDependencyManagement = false;

    @Inject private MavenProject project = null;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        super.execute();

        try {
            if (! isSkip()) {
                Set<URL> set = getLinkSet(project, includeDependencyManagement);
                Map<URL,List<Artifact>> map =
                    getResolvedOfflinelinkMap(project, includeDependencyManagement).entrySet().stream()
                    .collect(groupingBy(Map.Entry::getValue,
                                        mapping(Map.Entry::getKey, toList())));

                set.removeAll(map.keySet());

                generateOutput(set, map);
            } else {
                log.info("Skipping javadoc options file generation.");
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

    private void generateOutput(Set<URL> set, Map<URL,List<Artifact>> map) throws IOException {
        Path parent = outputDirectory.toPath();

        Files.createDirectories(parent);

        Path options = parent.resolve("options");

        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(options, CREATE, WRITE, TRUNCATE_EXISTING))) {
            for (URL url : set) {
                out.println("-link");
                out.println(url);
            }

            for (Map.Entry<URL,List<Artifact>> entry : map.entrySet()) {
                List<Artifact> artifacts = entry.getValue();

                for (Artifact artifact : artifacts) {
                    Path location = parent.resolve(ArtifactUtils.versionlessKey(artifact));

                    Files.createDirectories(location);

                    try (JarFile jar = new JarFile(artifact.getFile())) {
                        List<JarEntry> entries =
                            jar.stream()
                            .filter(t -> JAR_ENTRY_PATTERN.matcher(t.getName()).matches())
                            .collect(toList());

                        if (! entries.isEmpty()) {
                            for (JarEntry jarEntry : entries) {
                                try (InputStream in = jar.getInputStream(jarEntry)) {
                                    Files.copy(in, location.resolve(jarEntry.getName()), REPLACE_EXISTING);
                                }
                            }

                            Path packageList = location.resolve("package-list");
                            Path elementList = location.resolve("element-list");

                            if (! Files.exists(packageList)) {
                                Files.copy(elementList, packageList);
                            } else if (! Files.exists(elementList)) {
                                Files.copy(packageList, elementList);
                            }

                            out.println("-linkoffline");
                            out.println(entry.getKey());
                            out.println(location);
                        } else {
                            log.warn("{}: Location directory is empty; skipping...", location);
                        }
                    }
                }
            }
        }
    }
}
