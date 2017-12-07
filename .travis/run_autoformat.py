#!/usr/bin/env python
# -*- encoding: utf-8
"""
Wrapper script for doing auto-formatting in Travis.

In particular, this script will autoformat Terraform and Scala, then commit
and push the results.  It also lints Python and JSON, but those aren't
auto-formatted (yet).
"""

import os

from tooling import changed_files, git, make


if __name__ == '__main__':
    make('format')

    # https://graysonkoonce.com/getting-the-current-branch-name-during-a-pull-request-in-travis-ci/
    if os.environ['TRAVIS_PULL_REQUEST'] == 'false':
        branch = os.environ['TRAVIS_BRANCH']
    else:
        branch = os.environ['TRAVIS_PULL_REQUEST_BRANCH']

    if changed_files():
        print(
            '*** There were changes from formatting, creating a commit',
            flush=True)

        git('config', 'user.name', 'Travis CI on behalf of Wellcome')
        git('config', 'user.email', 'wellcomedigitalplatform@wellcome.ac.uk')
        git('config', 'core.sshCommand', 'ssh -i deploy_key')
        git(
            'remote', 'add', 'ssh-origin',
            'git@github.com:wellcometrust/platform.git'
        )

        git('add', '--verbose', '--all')
        git('commit', '-m', 'Apply auto-formatting rules')
        git('push', 'ssh-origin', 'HEAD:%s' % branch)
    else:
        print('*** There were no changes from auto-formatting', flush=True)

    make('check-format')
