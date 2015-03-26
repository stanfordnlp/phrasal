import fileinput
import codecs
import gzip
from os.path import basename,splitext


class CatFile(fileinput.FileInput):
    """
    Implements cat functionality in a platform-independent way.
    Transparently handles unicode-encoded files.
    """
    def __init__(self, files):
        fileinput.FileInput.__init__(self, files=files, inplace=0, backup="", bufsize=0, mode="r", openhook=fileinput.hook_compressed)
    
    def __enter__(self):
        """
        """
        return self

    def __exit__(self, type, value, tb):
        """
        """
        self.close()

