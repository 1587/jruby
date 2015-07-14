# replace this with a saner integration between maven3 and debhelper
DEB_BUILDDIR = .
JAVACMD = $(JAVA_HOME)/bin/java
DEB_MAVEN_REPO := $(CURDIR)/debian/maven-repo
MAVEN_HOME = /usr/share/maven
DEB_CLASSPATH = $(MAVEN_HOME)/boot/plexus-classworlds-2.x.jar
DEB_MAVEN_INVOKE = cd $(DEB_BUILDDIR) && $(JAVACMD) -classpath $(DEB_CLASSPATH) \
                 $(JAVA_OPTS) -Dclassworlds.conf=$(CURDIR)/debian/m2.conf \
                 -Dmaven.home=/usr/share/maven \
                 -Dmaven.multiModuleProjectDirectory=$(CURDIR) \
                 org.codehaus.plexus.classworlds.launcher.Launcher \
                 -s/etc/maven2/settings-debian.xml \
                 -Dmaven.repo.local=$(DEB_MAVEN_REPO) \
                 $(if $(DEB_MAVEN_ARGS_$(cdbs_curpkg)),$(DEB_MAVEN_ARGS_$(cdbs_curpkg)),$(DEB_MAVEN_ARGS))
