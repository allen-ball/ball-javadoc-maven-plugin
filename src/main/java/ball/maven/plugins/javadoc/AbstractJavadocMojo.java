package ball.maven.plugins.javadoc;
/*-
 * ##########################################################################
 * Javadoc Maven Plugin
 * %%
 * Copyright (C) 2021, 2022 Allen D. Ball
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
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import javax.inject.Inject;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.settings.Settings;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolver;

import static java.util.stream.Collectors.toCollection;
import static lombok.AccessLevel.PROTECTED;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Abstract base class for javadoc {@link org.apache.maven.plugin.Mojo}s.
 *
 * {@bean.info}
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 */
@NoArgsConstructor(access = PROTECTED) @Getter @ToString @Slf4j
public abstract class AbstractJavadocMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project.remoteArtifactRepositories}", readonly = true, required = true)
    private List<ArtifactRepository> remoteRepositories = Collections.emptyList();

    @Parameter(required = false)
    private Link[] links = new Link[] { };

    @Parameter(required = false)
    private Offlinelink[] offlinelinks = new Offlinelink[] { };

    @Parameter(defaultValue = "false", property = "javadoc.skip")
    private boolean skip = false;

    @Inject private MavenSession session = null;
    @Inject private ArtifactHandlerManager manager = null;
    @Inject private RepositorySystem system = null;
    @Inject private ArtifactResolver resolver = null;

    /**
     * Method to produce a {@link Stream} of
     * {@link MavenProject#getDependencies()} and
     * {@link MavenProject#getDependencyManagement()}
     * {@link Dependency Dependencies}.
     *
     * @param   project         The {@link MavenProject}.
     *
     * @return  The {@link Dependency} {@link Stream}.
     */
    protected Stream<Dependency> getDependencyManagementStream(MavenProject project) {
        Stream<Dependency> stream =
            Stream.of(project.getDependencies(), project.getDependencyManagement().getDependencies())
            .filter(Objects::nonNull)
            .flatMap(Collection::stream);

        return stream;
    }

    /**
     * Method to get the {@link Set} of {@link Link} {@link URL}s.
     *
     * @param   project         The {@link MavenProject}.
     * @param   includeDependencyManagement
     *                          Whether or not to include dependency
     *                          management in the analysis.
     *
     * @return  The {@link Set} of {@link Link} {@link URL}s.
     */
    protected Set<URL> getLinkSet(MavenProject project, boolean includeDependencyManagement) {
        Set<URL> set = new LinkedHashSet<>();

        for (Link link : links) {
            project.getArtifacts().stream()
                .filter(link::include)
                .map(link::getUrl)
                .forEach(set::add);
        }

        if (includeDependencyManagement) {
            for (Link link : links) {
                getDependencyManagementStream(project)
                    .filter(t -> isNotBlank(t.getVersion()))
                    .map(JavadocArtifact::new)
                    .filter(link::include)
                    .map(link::getUrl)
                    .forEach(set::add);
            }
        }

        return set;
    }

    /**
     * Method to get the {@link Map} of {@link Offlinelink}
     * {@link Artifact}s to {@link URL}s.
     *
     * @param   project         The {@link MavenProject}.
     * @param   includeDependencyManagement
     *                          Whether or not to include dependency
     *                          management in the analysis.
     *
     * @return  The {@link Map} of {@link Offlinelink} {@link Artifact}s to
     *          {@link URL}s.
     */
    protected Map<Artifact,URL> getResolvedOfflinelinkMap(MavenProject project, boolean includeDependencyManagement) {
        TreeMap<Artifact,URL> map = new TreeMap<>(Comparator.comparing(ArtifactUtils::versionlessKey));

        for (Offlinelink offlinelink : offlinelinks) {
            project.getArtifacts().stream()
                .filter(t -> Objects.equals(t.getType(), "jar"))
                .filter(t -> Objects.equals(t.getClassifier(), "javadoc"))
                .filter(offlinelink::include)
                .forEach(t -> map.putIfAbsent(t, offlinelink.getUrl()));
        }

        Set<Artifact> artifacts =
            project.getArtifacts().stream()
            .filter(t -> Objects.equals(t.getType(), "jar"))
            .filter(t -> isBlank(t.getClassifier()))
            .map(JavadocArtifact::new)
            .collect(toCollection(() -> new TreeSet<>(map.comparator())));

        if (includeDependencyManagement) {
            getDependencyManagementStream(project)
                .filter(t -> Objects.equals(t.getType(), "jar"))
                .filter(t -> isBlank(t.getClassifier()))
                .filter(t -> isNotBlank(t.getVersion()))
                .map(JavadocArtifact::new)
                .forEach(artifacts::add);
        }

        artifacts.removeAll(map.keySet());

        ProjectBuildingRequest request = getProjectBuildingRequest();

        for (Offlinelink offlinelink : offlinelinks) {
            Set<Artifact> set =
                artifacts.stream()
                .filter(offlinelink::include)
                .collect(toCollection(LinkedHashSet::new));

            for (Artifact artifact : set) {
                URL url = map.get(artifact);

                if (url == null) {
                    log.info("Resolving {}...", artifact);

                    try {
                        map.putIfAbsent(resolver.resolveArtifact(request, artifact).getArtifact(),
                                        offlinelink.getUrl(artifact));
                    } catch (Exception exception) {
                        log.warn("{}: {}", artifact, exception.getMessage());
                        log.debug("{}", exception);
                    }
                } else {
                    if (! Objects.equals(url, offlinelink.getUrl(artifact))) {
                        log.warn("{} matches {} but was previously resolved with {}", artifact, offlinelink, url);
                    }
                }
            }
        }

        return map;
    }

    private ProjectBuildingRequest getProjectBuildingRequest() {
        List<ArtifactRepository> repoList = getRemoteRepositories();
        Settings settings = session.getSettings();

        system.injectMirror(repoList, settings.getMirrors());
        system.injectProxy(repoList, settings.getProxies());
        system.injectAuthentication(repoList, settings.getServers());

        ProjectBuildingRequest request = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());

        request.setRemoteRepositories(repoList);

        return request;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
    }

    private class JavadocArtifact extends DefaultArtifact {
        public JavadocArtifact(Artifact artifact) {
            this(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
        }

        public JavadocArtifact(Dependency dependency) {
            this(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion());
        }

        public JavadocArtifact(String gav) { this(gav.split("[:]")); }

        public JavadocArtifact(String... gav) {
            super(gav.length > 0 ? gav[0] : EMPTY,
                  gav.length > 1 ? gav[1] : EMPTY,
                  gav.length > 2 ? gav[gav.length - 1] : EMPTY,
                  EMPTY, "jar", "javadoc", manager.getArtifactHandler("jar"));
        }
    }
}
