FROM openjdk:11.0.1-jdk

LABEL maintainer="dev@redotter.sg"

#
# Copy the files we need
#

COPY script/start.sh /start.sh
COPY config/config.yaml /config/config.yaml
COPY build/libs/bq-metrics-extractor-uber.jar /bq-metrics-extractor.jar

#
# Load, then dump the standard java classes into the
# image being built, to speed up java load time
# using Class Data Sharing. The "quit" command will
# simply quit the program after it's dumped the list of
# classes that should be cached.
#

RUN ["java", "-Dfile.encoding=UTF-8", "-Xshare:on", "-Xshare:dump", "-jar", "/bq-metrics-extractor.jar", "quit", "config/config.yaml"]


#
# Finally the actual entry point
#

ENTRYPOINT ["/start.sh"]
