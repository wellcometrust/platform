#!/usr/bin/env python
# -*- encoding: utf-8 -*-

from __future__ import print_function

import subprocess


# Root of the Git repository
ROOT = subprocess.check_output([
    'git', 'rev-parse', '--show-toplevel']).decode('ascii').strip()


def fprint(*args, **kwargs):
    kwargs['flush'] = True
    print(*args, **kwargs)


def changed_files(*args):
    """
    Returns a set of changed files in a given commit range.

    :param commit_range: Arguments to pass to ``git diff``.
    """
    files = set()
    command = ['git', 'diff', '--name-only'] + list(args)
    diff_output = subprocess.check_output(command).decode('ascii')
    for line in diff_output.splitlines():
        filepath = line.strip()
        if filepath:
            files.add(filepath)
    return files


def affects_tests(path, task):
    """Does this file have any effect on test outcomes?"""
    # Nothing reads our Markdown files in tests, so we can ignore their
    # effect here.
    if path.endswith(('.md', '.png')) or path == 'LICENSE':
        return False

    # The ``misc`` folder is never used anywhere, so we can ignore it.
    if path.startswith('misc/'):
        return False

    # We do lint the JSON and TTL files in the ontologies directory when
    # running in CI, but not in any of the code tests.
    #
    # Since linting already happens unconditionally, we can ignore changes
    # to this directory as well.
    if path.startswith('ontologies/'):
        return False

    # For each task, which directories only have an effect on this task?
    #
    # For example, changes to the ``loris`` directory only have an effect
    # on the ``loris`` task; in any other task changes to that directory
    # can be ignored.
    task_specific_directories = {
        'loris': ['loris'],
        'sierra_adapter': ['sierra_adapter'],
        'id_minter': ['catalogue_pipeline/id_minter'],
        'ingestor': ['catalogue_pipeline/ingestor'],
        'reindexer': ['catalogue_pipeline/reindexer'],
        'transformer': ['catalogue_pipeline/transformer'],
        'api': ['catalogue_api'],
        'monitoring': ['monitoring'],
        'shared_infra': ['shared_infra'],
        'nginx': ['nginx'],
    }

    # If we have a change to a file which is specific to a particular task,
    # but we're *not* in that task, this change is unimportant.
    for prefix, directories in task_specific_directories.items():
        if not task.startswith(prefix) and path.startswith(tuple(directories)):
            return False

        if task.startswith(prefix) and path.startswith(tuple(directories)):
            return True

    # The top-level common directory contains some Scala files which are
    # shared across multiple projects.  If we're definitely in a project
    # which doesn't use this sbt-common lib, we can ignore changes to it.
    sbt_free_tasks = ('loris', 'monitoring', 'shared_infra')
    if task.startswith(sbt_free_tasks) and path.startswith('common/'):
        return False

    # Otherwise, we were unable to decide if this change was important.
    # We assume that it is, so we'll run tests just in case.
    return True


def are_there_job_relevant_changes(changed_files, task):
    """
    Given a list of changed files and a Make task, are there any reasons
    why the output of this task might have changed?

    e.g. if the loris/ directory changes, we should re-run tasks associated
         with Loris.

    Returns a list of reasons why the task should be run.
    """
    reasons = []

    # These tests are usually fast; we always run them rather than trying
    # to keep up-to-date rules of exactly which changed files mean they
    # should run.
    if task in [
        'travis-format'
    ]:
        reasons.append('We always run the %s task' % task)

    # These files are so fundamental to the build process that if they change,
    # we should do a complete rebuild just to be safe.
    if any(f in changed_files for f in [
        'Makefile',
        'functions.Makefile',
        'shared.Makefile'
    ]):
        reasons.append('Changes to a core Makefile')

    if any(f.startswith('.travis') for f in changed_files):
        reasons.append('Changes to the Travis config')

    if any(f.startswith('builds/') for f in changed_files):
        reasons.append('Changes to the build scripts')
    # Since it's better to run tests when we didn't need to, than skip tests
    # when it was important, we remove any files which we know are safe to
    # ignore, and run tests if there's anything left.
    interesting_changed_files = [
        f for f in changed_files if affects_tests(f, task=task)
    ]

    if interesting_changed_files:
        reasons.append(
            'Changes to the following files mean we need to run tests: %s' %
            ', '.join(interesting_changed_files)
        )

    return reasons


def make_decision(changed_files, task, action):
    reasons = are_there_job_relevant_changes(
        changed_files=changed_files, task=task
    )
    if reasons:
        print('*** Reasons to %s for this change:' % action)
        for r in reasons:
            print('***   - %s' % r)
        return True
    else:
        print('*** No reasons to %s for this change!' % action)
        return False


def make(task):
    command = ['make', task]
    print('*** Running %r' % command, flush=True)
    subprocess.check_call(command)


def git(*args):
    command = ['git'] + list(args)
    print('*** Running %r' % command, flush=True)
    subprocess.check_call(command)


def rreplace(string, old, new, count=None):
    """
    Replace ``old`` with ``new``, starting from the right.
    """
    # Split on the occurrences of ``old``, starting from the right
    parts = string.rsplit(old, count)

    # Then join back together with ``new``
    return new.join(parts)
