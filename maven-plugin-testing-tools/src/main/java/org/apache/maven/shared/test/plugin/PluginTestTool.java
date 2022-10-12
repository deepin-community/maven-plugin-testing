package org.apache.maven.shared.test.plugin;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.util.FileUtils;

import java.io.File;
import java.io.IOException;

/**
 * Test tool that provides a single point of access for staging a plugin artifact - along with its
 * POM lineage - into a clean test-time local repository. This involves modifying the plugin POM to
 * provide a stable test-time version for test-build POMs to reference, then installing the plugin
 * jar and associated POMs (including those ancestors that are reachable using &lt;relativePath&gt;)
 * into the test local repository.
 *
 * <p>
 * <b>WARNING:</b> Currently, the <code>RepositoryTool</code> will not
 * resolve parent POMs that exist <b>only</b> in your normal local repository, and are not reachable
 * using the relativePath element. This may result in failed test builds, as one or more of the
 * plugin's ancestor POMs cannot be resolved.
 * </p>
 *
 * @author jdcasey
 * @version $Id$
 */
@Deprecated
@Component( role = PluginTestTool.class )
public class PluginTestTool
{
    /** Plexus role */
    public static final String ROLE = PluginTestTool.class.getName();

    @Requirement
    private ProjectTool projectTool;

    @Requirement
    private RepositoryTool repositoryTool;

    /**
     * Stage the plugin, using a stable version, into a temporary local-repository directory that is
     * generated by this method. When the plugin is staged, return the local repository base directory
     * for use in test builds.
     *
     * @param pomFile current POM file
     * @param testVersion The test version for the plugin, used for reference in test-build POMs and
     *   fully-qualified goals
     * @return The base-directory location of the generated local repository
     * @throws TestToolsException if any
     */
    public File preparePluginForIntegrationTesting( File pomFile, String testVersion )
        throws TestToolsException
    {
        return prepareForTesting( pomFile, testVersion, false, null );
    }

    /**
     * Stage the plugin, using a stable version, into a temporary local-repository directory that is
     * generated by this method. When the plugin is staged, return the local repository base directory
     * for use in test builds. This method also skips unit testing during plugin jar production,
     * since it is assumed that executing these tests would lead to a recursive test-and-build loop.
     *
     * @param pomFile current POM file
     * @param testVersion The test version for the plugin, used for reference in test-build POMs and
     *   fully-qualified goals
     * @return The base-directory location of the generated local repository
     * @throws TestToolsException if any
     */
    public File preparePluginForUnitTestingWithMavenBuilds( File pomFile, String testVersion )
        throws TestToolsException
    {
        return prepareForTesting( pomFile, testVersion, true, null );
    }

    /**
     * Stage the plugin, using a stable version, into the specified local-repository directory.
     * When the plugin is staged, return the local repository base directory for verification.
     *
     * @param pomFile current POM file
     * @param testVersion The test version for the plugin, used for reference in test-build POMs and
     *   fully-qualified goals
     * @param localRepositoryDir The base-directory location of the test local repository, into which
     *   the plugin's test version should be staged.
     * @return The base-directory location of the generated local repository
     * @throws TestToolsException if any
     */
    public File preparePluginForIntegrationTesting( File pomFile, String testVersion, File localRepositoryDir )
        throws TestToolsException
    {
        return prepareForTesting( pomFile, testVersion, false, localRepositoryDir );
    }

    /**
     * Stage the plugin, using a stable version, into the specified local-repository directory.
     * When the plugin is staged, return the local repository base directory for verification. This
     * method also skips unit testing during plugin jar production, since it is assumed that
     * executing these tests would lead to a recursive test-and-build loop.
     *
     * @param pomFile current POM file
     * @param testVersion The test version for the plugin, used for reference in test-build POMs and
     *   fully-qualified goals
     * @param localRepositoryDir The base-directory location of the test local repository, into which
     *   the plugin's test version should be staged.
     * @return The base-directory location of the generated local repository
     * @throws TestToolsException if any
     */
    public File preparePluginForUnitTestingWithMavenBuilds( File pomFile, String testVersion, File localRepositoryDir )
        throws TestToolsException
    {
        return prepareForTesting( pomFile, testVersion, true, localRepositoryDir );
    }

    private File prepareForTesting( File pomFile, String testVersion, boolean skipUnitTests, File localRepositoryDir )
        throws TestToolsException
    {
        File realProjectDir = pomFile.getParentFile();

        try
        {
            realProjectDir = realProjectDir.getCanonicalFile();
        }
        catch ( IOException e )
        {
            throw new TestToolsException( "Failed to stage plugin for testing.", e );
        }

        try
        {
            final File tmpDir = File.createTempFile( "plugin-IT-staging-project", "" );

            tmpDir.delete();

            tmpDir.mkdirs();

            Runtime.getRuntime().addShutdownHook( new Thread( new Runnable()
            {

                public void run()
                {
                    try
                    {
                        FileUtils.deleteDirectory( tmpDir );
                    }
                    catch ( IOException e )
                    {
                        // it'll get cleaned up when the temp dir is purged next...
                    }
                }

            } ) );

            FileUtils.copyDirectoryStructure( realProjectDir, tmpDir );
        }
        catch ( IOException e )
        {
            throw new TestToolsException( "Failed to create temporary staging directory for plugin project.", e );
        }

        File buildLog = new File( "target/test-build-logs/setup.build.log" );

        buildLog.getParentFile().mkdirs();

        File localRepoDir = localRepositoryDir;

        if ( localRepoDir == null )
        {
            localRepoDir = new File( "target/test-local-repository" );
        }

        MavenProject project = projectTool.packageProjectArtifact( pomFile, testVersion, skipUnitTests, buildLog );

        repositoryTool.createLocalRepositoryFromComponentProject( project, new File( realProjectDir, "pom.xml" ),
                                                                  localRepoDir );

        return localRepoDir;
    }

}
