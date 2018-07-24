import gettext

from pycman.config import PacmanConfig

t = gettext.translation('rhn-client-tools', fallback=True)
if not hasattr(t, 'ugettext'):
    t.ugettext = t.gettext
_ = t.ugettext


def verifyPackages(packages):
    conf = PacmanConfig('/etc/pacman.conf')
    handle = conf.initialize_alpm()
    db = handle.get_localdb()

    missing_packages = []
    for required_package in packages:
        search_name = required_package[0]
        if len(required_package) > 1 and required_package[1]:
            search_name += "-" + str(required_package[1])
            if len(required_package) > 2 and required_package[2]:
                search_name += "-" + str(required_package[2])
                if len(required_package) > 4 and required_package[4]:
                    search_name += "-" + str(required_package[4])
        results = db.search(search_name)
        if len(results) == 0:
            missing_packages.append(required_package)

    return [], missing_packages


def getInstalledPackageList(msgCallback = None, progressCallback = None,
                            getArch=None, getInfo = None):
    if msgCallback is not None:
        msgCallback(_("Getting list of packages installed on the system"))

    conf = PacmanConfig('/etc/pacman.conf')
    handle = conf.initialize_alpm()
    db = handle.get_localdb()

    count = 0
    total = len(db.pkgcache)
    packages = []
    for package in db.pkgcache:
        full_version = package.version

        epoch = 1
        possible_epoch = full_version.split(':')
        if len(possible_epoch) == 2:
            epoch = possible_epoch[0]
            full_version = possible_epoch[1]

        release = 1
        possible_release = full_version.split('-')
        if len(possible_release) > 1:
            release = possible_release[-1]
            version = "-".join(possible_release[0:-1])
        else:
            version = full_version

        packages.append({
            'name': package.name,
            'epoch': epoch,
            'version': version,
            'release': release,
            'arch': package.arch,
            'installtime': package.installdate
        })

        packages.append(package)

        count += 1
        if progressCallback is not None:
            progressCallback(count, total)

    packages.sort(key=lambda x:(x['name'], x['epoch'], x['version'], x['release']))
    return packages


def setDebugVerbosity():
    pass
