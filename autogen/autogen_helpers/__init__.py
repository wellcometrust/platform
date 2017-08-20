# -*- encoding: utf-8

import csv
import os
import subprocess

from jinja2 import Environment, PackageLoader


# Root of the Git repository
ROOT = subprocess.check_output([
    'git', 'rev-parse', '--show-toplevel'
]).decode('ascii').strip()

REFERENCE_DATA = os.path.join(ROOT, 'ontologies', 'Reference data')


def run_autogen(datafile, outfile, template_name):
    print(f'*** Running autogen for {outfile}')
    os.makedirs(os.path.dirname(outfile), exist_ok=True)

    with open(os.path.join(REFERENCE_DATA, datafile)) as csvfile:
        data = list(csv.DictReader(csvfile))

    env = Environment(loader=PackageLoader('autogen_helpers', 'templates'))
    template = env.get_template(template_name)

    with open(outfile, 'w') as f:
        f.write(template.render(data=data))
