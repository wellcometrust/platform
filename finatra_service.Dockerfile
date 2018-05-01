FROM wellcome/finatra_service_base

ADD target/universal/stage /opt/docker

USER root
RUN chown -R daemon:daemon /opt/docker

ARG NAME
ENV project=$NAME

USER daemon
CMD ["/run.sh"]
