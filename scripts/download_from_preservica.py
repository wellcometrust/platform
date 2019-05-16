#!/usr/bin/env python
# -*- encoding: utf-8

import getpass
from pathlib import Path
import sys

import click
import bs4
import requests
from requests.auth import HTTPBasicAuth


API_URL = "http://sdb.wellcome.ac.uk/sdb/rest"


def get_xml(sess, path, url):
    try:
        return bs4.BeautifulSoup(path.read_bytes(), "xml")
    except FileNotFoundError:
        resp = sess.get(url)
        resp.raise_for_status()
        path.write_bytes(resp.content)
        return bs4.BeautifulSoup(resp.content, "xml")


def download_file(sess, url, path):
    # https://stackoverflow.com/q/16694907/1558022
    with sess.get(url, stream=True) as r:
        r.raise_for_status()
        with open(path, 'wb') as f:
            for chunk in r.iter_content(chunk_size=8192):
                if chunk:
                    f.write(chunk)



@click.command()
@click.argument("PRESERVICA_GUID")
def download_from_preservica(preservica_guid):
    password = getpass.getpass("Active Directory password? (Yes, really) ")

    sess = requests.Session()
    sess.auth = HTTPBasicAuth(getpass.getuser(), password)

    working_dir = Path(preservica_guid)
    working_dir.mkdir(exist_ok=True)

    # Get the initial deliverable unit
    du_xml = get_xml(
        sess,
        path=working_dir / "deliverable_unit.xml",
        url=f"{API_URL}/deliverableUnits/{preservica_guid}"
    )
    print(f"*** Fetched deliverable unit XML for: {preservica_guid}")

    manifestation_links = [
        link_tag.attrs["href"]
        for link_tag in du_xml.find_all("link", attrs={"rel": "manifestation"})
    ]

    if len(manifestation_links) == 1:
        manifest_link = manifestation_links[0]
        manifest_guid = manifest_link.split('/')[-1]
        print(f"*** Detected manifestation ID:        {manifest_guid}")
    else:
        sys.exit(f"*** Could not detect a manifestation link: {manifestation_links}")

    manifest_xml = get_xml(
        sess,
        path=working_dir / "manifestation.xml",
        url=manifest_link
    )
    print(f"*** Fetched manifestation XML for:    {manifest_guid}")

    for entry in manifest_xml.find_all("entry"):
        entry_guid = entry.find("id").text

        entry_url = entry.find(
            "link", attrs={"rel": "digital-file-content"}).attrs["href"]
        entry_title = entry.find("title").text

        if (working_dir / entry_title).exists():
            print(f"··· Already downloaded asset:         {entry_guid}")
            continue
        else:
            print(f"*** Fetching asset:                   {entry_guid}")
            download_file(sess, entry_url, path = working_dir / entry_title)


if __name__ == "__main__":
    download_from_preservica()
