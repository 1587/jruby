/***** BEGIN LICENSE BLOCK *****
 * Version: EPL 1.0/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Eclipse Public
 * License Version 1.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.eclipse.org/legal/epl-v10.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Copyright (C) 2006 Ola Bini <ola@ologix.com>
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"),
 * or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the EPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the EPL, the GPL or the LGPL.
 ***** END LICENSE BLOCK *****/
/**
 * $Id$
 */
package org.jruby.util;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;

import jnr.posix.JavaSecuredFile;
import jnr.posix.POSIX;

import jnr.posix.POSIXFactory;
import org.jruby.Ruby;
import org.jruby.platform.Platform;
import org.jruby.runtime.ThreadContext;

/**
 * <p>This file acts as an alternative to NormalizedFile, due to the problems with current working
 * directory.</p>
 *
 */
public class JRubyFile extends JavaSecuredFile {
    private static final long serialVersionUID = 435364547567567L;

    public static JRubyFile create(String cwd, String pathname) {
        return createNoUnicodeConversion(cwd, pathname);
    }

    public static FileResource createResource(ThreadContext context, String pathname) {
      return createResource(context.runtime, pathname);
    }

    public static FileResource createRestrictedResource(String cwd, String pathname) {
      return createResource(POSIXFactory.getJavaPOSIX(), null, cwd, pathname);
    }

    public static FileResource createResource(Ruby runtime, String pathname) {
      return createResource(runtime.getPosix(), runtime, runtime.getCurrentDirectory(), pathname);
    }

    public static FileResource createResource(POSIX posix, String cwd, String pathname) {
      return createResource(posix, null, cwd, pathname);
    }

    public static FileResource createResource(POSIX posix, Ruby runtime, String cwd, String pathname) {
        FileResource emptyResource = EmptyFileResource.create(pathname);
        if (emptyResource != null) return emptyResource;

        // This will work against anything potentially containing a '!' in it and does not require a scheme.
        // (see test/test_java_on_load_path.rb: $LOAD_PATH << "test/test_jruby_1332.jar!"; require 'test_jruby_1332.rb'
        FileResource jarResource = JarResource.create(pathname);
        if (jarResource != null) return jarResource;

        if (pathname.indexOf(':') > 0) { // scheme-oriented resources
            if (pathname.startsWith("classpath:")) return ClasspathResource.create(pathname);

            // replace is needed for maven/jruby-complete/src/it/app_using_classpath_uri to work
            if (pathname.startsWith("uri:")) return URLResource.create(runtime, pathname.replace("classpath:/", ""));

            if (pathname.startsWith("file:")) {
                pathname = pathname.substring(5);

                if (pathname.length() == 0) return EmptyFileResource.create(pathname);
            }
        }

        File internal = new JavaSecuredFile(pathname);
        if (cwd != null && !internal.isAbsolute() && (cwd.startsWith("uri:") || cwd.startsWith("file:"))) {
            return createResource(posix, runtime, null, cwd + '/' + pathname);
        }

        // If any other special resource types fail, count it as a filesystem backed resource.
        JRubyFile f = create(cwd, pathname);
        return new RegularFileResource(posix, f);
    }

    public static String normalizeSeps(String path) {
        if (Platform.IS_WINDOWS) {
            return path.replace(File.separatorChar, '/');
        }
        return path;
    }

    private static JRubyFile createNoUnicodeConversion(String cwd, String pathname) {
        if (pathname == null || pathname.length() == 0 || Ruby.isSecurityRestricted()) {
            return JRubyNonExistentFile.NOT_EXIST;
        }
        if(pathname.startsWith("file:")) {
            pathname = pathname.substring(5);
        }
        File internal = new JavaSecuredFile(pathname);
        if (internal.isAbsolute()) {
            return new JRubyFile(internal);
        }
        if(cwd != null && cwd.startsWith("uri:") && !pathname.startsWith("uri:") && !pathname.contains("!/")) {
            return new JRubyFile(cwd + '/' + pathname);
        }
        internal = new JavaSecuredFile(cwd, pathname);
        if(!internal.isAbsolute()) {
            throw new IllegalArgumentException("Neither current working directory ("+cwd+") nor pathname ("+pathname+") led to an absolute path");
        }
        return new JRubyFile(internal);
    }

    public static String getFileProperty(String property) {
        return normalizeSeps(SafePropertyAccessor.getProperty(property, "/"));
    }

    private JRubyFile(File file) {
        this(file.getAbsolutePath());
    }

    protected JRubyFile(String filename) {
        super(filename);
    }

    @Override
    public String getAbsolutePath() {
        final String path = super.getPath();
        if (path.startsWith("uri:")) {
            // TODO better do not collapse // to / for uri: files
            return path.replaceFirst(":/([^/])", "://$1" );
        }
        return normalizeSeps(new File(path).getAbsolutePath());
    }

    @Override
    public String getCanonicalPath() throws IOException {
        try {
            return normalizeSeps(super.getCanonicalPath());
        }
        catch (IOException e) {
            // usually IOExceptions don't tell us anything about the path,
            // so add an extra wrapper to give more debugging help.
            throw new IOException("Unable to canonicalize path: " + getAbsolutePath(), e);
        }
    }

    @Override
    public String getPath() {
	return normalizeSeps(super.getPath());
    }

    @Override
    public String toString() {
        return normalizeSeps(super.toString());
    }

    @Override
    public File getAbsoluteFile() {
        return new JRubyFile(getAbsolutePath());
    }

    @Override
    public File getCanonicalFile() throws IOException {
        return new JRubyFile(getCanonicalPath());
    }

    @Override
    public String getParent() {
        String parent = super.getParent();
        if (parent != null) {
            parent = normalizeSeps(parent);
        }
        return parent;
    }

    @Override
    public File getParentFile() {
        String parent = getParent();
        if (parent == null) return this;
        return new JRubyFile(parent);
    }

    public static File[] listRoots() {
        File[] roots = File.listRoots();
        JRubyFile[] smartRoots = new JRubyFile[roots.length];
        for(int i = 0, j = roots.length; i < j; i++) {
            smartRoots[i] = new JRubyFile(roots[i].getPath());
        }
        return smartRoots;
    }

    public static File createTempFile(String prefix, String suffix, File directory) throws IOException {
        return new JRubyFile(File.createTempFile(prefix, suffix,directory));
    }

    public static File createTempFile(String prefix, String suffix) throws IOException {
        return new JRubyFile(File.createTempFile(prefix, suffix));
    }

    @Override
    public String[] list(FilenameFilter filter) {
        String[] files = super.list(filter);
        if (files == null) {
            return null;
        }

        String[] smartFiles = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            smartFiles[i] = normalizeSeps(files[i]);
        }
        return smartFiles;
    }

    @Override
    public File[] listFiles() {
        File[] files = super.listFiles();
        if (files == null) {
            return null;
        }

        JRubyFile[] smartFiles = new JRubyFile[files.length];
        for (int i = 0, j = files.length; i < j; i++) {
            smartFiles[i] = createNoUnicodeConversion(super.getAbsolutePath(), files[i].getPath());
        }
        return smartFiles;
    }

    @Override
    public File[] listFiles(final FileFilter filter) {
        final File[] files = super.listFiles(filter);
        if (files == null) {
            return null;
        }

        JRubyFile[] smartFiles = new JRubyFile[files.length];
        for (int i = 0,j = files.length; i < j; i++) {
            smartFiles[i] = createNoUnicodeConversion(super.getAbsolutePath(), files[i].getPath());
        }
        return smartFiles;
    }

    @Override
    public File[] listFiles(final FilenameFilter filter) {
        final File[] files = super.listFiles(filter);
        if (files == null) {
            return null;
        }

        JRubyFile[] smartFiles = new JRubyFile[files.length];
        for (int i = 0,j = files.length; i < j; i++) {
            smartFiles[i] = createNoUnicodeConversion(super.getAbsolutePath(), files[i].getPath());
        }
        return smartFiles;
    }
}
