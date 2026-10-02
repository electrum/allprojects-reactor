/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ca.vanzyl.maven.allprojectsreactor;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.repository.internal.MavenWorkspaceReader;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.repository.WorkspaceRepository;

import javax.inject.Named;
import javax.inject.Singleton;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Installs {@link AllProjectsReactorWorkspaceReader} on the repository session.
 * <p>
 * Maven 3.9 only picks up {@code WorkspaceReader} components from project class realms, and a project has a class realm
 * only when it declares a build extension. Core extension lifecycle participants are always found, and the workspace
 * reader set on the repository session here is chained after Maven's own reactor reader.
 */
@Named
@Singleton
public final class AllProjectsReactorLifecycleParticipant
        extends AbstractMavenLifecycleParticipant
{
    @Override
    public void afterSessionStart(MavenSession session)
    {
        RepositorySystemSession repositorySession = session.getRepositorySession();
        if (!(repositorySession instanceof DefaultRepositorySystemSession defaultRepositorySession)) {
            return;
        }

        WorkspaceReader reader = new AllProjectsReactorWorkspaceReader(session);
        WorkspaceReader existing = defaultRepositorySession.getWorkspaceReader();
        defaultRepositorySession.setWorkspaceReader(existing == null ? reader : new ChainedReader(existing, reader));
    }

    private record ChainedReader(WorkspaceReader first, WorkspaceReader second)
            implements MavenWorkspaceReader
    {
        @Override
        public WorkspaceRepository getRepository()
        {
            return second.getRepository();
        }

        @Override
        public File findArtifact(Artifact artifact)
        {
            File file = first.findArtifact(artifact);
            return file != null ? file : second.findArtifact(artifact);
        }

        @Override
        public List<String> findVersions(Artifact artifact)
        {
            Set<String> versions = new LinkedHashSet<>(first.findVersions(artifact));
            versions.addAll(second.findVersions(artifact));
            return List.copyOf(versions);
        }

        @Override
        public Model findModel(Artifact artifact)
        {
            if (first instanceof MavenWorkspaceReader firstReader) {
                Model model = firstReader.findModel(artifact);
                if (model != null) {
                    return model;
                }
            }
            return second instanceof MavenWorkspaceReader secondReader ? secondReader.findModel(artifact) : null;
        }
    }
}
