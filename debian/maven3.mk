MAVEN_HOME = /usr/share/maven
DEB_MAVEN_ARGS := -Pdist
DEB_CLASSPATH = $(MAVEN_HOME)/boot/plexus-classworlds-2.x.jar
DEB_MAVEN_INVOKE = cd $(DEB_BUILDDIR) && $(JAVACMD) -classpath $(DEB_CLASSPATH) \
                 $(JAVA_OPTS) -Dclassworlds.conf=$(CURDIR)/debian/m2.conf \
                 org.codehaus.plexus.classworlds.launcher.Launcher \
                 -s/etc/maven2/settings-debian.xml \
                 -Dmaven.repo.local=$(DEB_MAVEN_REPO) \
                 $(if $(DEB_MAVEN_ARGS_$(cdbs_curpkg)),$(DEB_MAVEN_ARGS_$(cdbs_curpkg)),$(DEB_MAVEN_ARGS))

maven-sanity-check:
	@if ! test -x "$(JAVACMD)"; then \
		echo "You must specify a valid JAVA_HOME or JAVACMD!"; \
		exit 1; \
	fi
	@if ! test -r "$(MAVEN_HOME)/boot/plexus-classworlds-2.x.jar"; then \
		echo "You must specify a valid MAVEN_HOME directory!"; \
		exit 1; \
	fi
