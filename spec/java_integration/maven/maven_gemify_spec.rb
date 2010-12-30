require File.dirname(__FILE__) + "/../spec_helper"

require 'rubygems'
require 'rubygems/format'
require 'rubygems/maven_gemify'
require 'yaml'

begin
  Gem::Maven::Gemify.verbose = true if $DEBUG || ENV['DEBUG']
  Gem::Maven::Gemify.maven_get

  describe Gem::Specification, "maven_name?" do
    it "matches dot-separated artifacts" do
      Gem::Specification.maven_name?('commons-lang.commons-lang').should be_true
    end

    it "matches colon-separated artifacts" do
      Gem::Specification.maven_name?('commons-lang:commons-lang').should be_true
    end

    it "does not match things that look like a windows filename" do
      Gem::Specification.maven_name?('c:ommons-lang:commons-lang').should be_false
      Gem::Specification.maven_name?('c:/temp/somefile').should be_false
    end
  end

  describe Gem::Maven::Gemify do
    it "creates an instance of the Maven class" do
      Gem::Maven::Gemify.maven_get.should be_kind_of(org.apache.maven.Maven)
    end

    it "gets a list of versions for a maven artifact" do
      Gem::Maven::Gemify.get_versions("commons-lang.commons-lang").should include("2.5.0")
    end

    it "generates a gemspec file for the maven artifact" do
      require 'yaml'
      specfile = Gem::Maven::Gemify.generate_spec("commons-lang.commons-lang", "2.5.0")
      gemspec = Gem::Specification.from_yaml(File.read(specfile))
      gemspec.name.should == "commons-lang.commons-lang"
      gemspec.version.should == Gem::Version.new("2.5.0")
    end
  end
rescue => e
  puts(e, *e.backtrace) if $DEBUG || ENV['DEBUG']
  # Skipping maven specs w/o M3 or ruby-maven gem installed
end
