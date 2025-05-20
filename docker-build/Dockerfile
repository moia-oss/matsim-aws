FROM ubuntu:22.04
COPY build.sh /usr/share/matsimjobs/
RUN cd /usr/share/matsimjobs/ && ./build.sh
COPY run.sh sync-loop.sh /usr/share/matsimjobs/run/
RUN mkdir -p "/usr/share/matsimjobs/run"
WORKDIR "/usr/share/matsimjobs/run/"
ENTRYPOINT ["/bin/bash", "./run.sh"]
