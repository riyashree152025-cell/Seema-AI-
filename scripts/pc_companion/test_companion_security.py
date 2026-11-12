#!/usr/bin/env python3
"""Regression tests for the PC companion's security defaults. Run: python3 -I scripts/pc_companion/test_companion_security.py"""
import importlib.util
import os
import pathlib
import stat
import sys
import tempfile
import unittest

HERE = pathlib.Path(__file__).resolve().parent


def load_module(home):
    os.environ["HOME"] = home
    os.environ["USERPROFILE"] = home
    spec = importlib.util.spec_from_file_location("companion", HERE / "seema_pc_companion.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class CompanionSecurityTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.m = load_module(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def test_listens_on_loopback_by_default(self):
        self.assertEqual("127.0.0.1", self.m.load_config()["bind_host"])

    def test_a_strong_token_replaces_the_old_default(self):
        cfg = self.m.load_config()
        self.assertGreaterEqual(len(cfg["auth_token"]), 32)
        self.assertNotEqual("seema_pc_secret_key_12345", cfg["auth_token"])

    @unittest.skipIf(os.name == "nt", "POSIX file modes")
    def test_token_and_memories_are_owner_only(self):
        self.m.load_config()
        for path in (self.m.CONFIG_FILE, self.m.MEMORIES_FILE, self.m.CONVERSATION_FILE):
            self.assertEqual(0o600, stat.S_IMODE(os.stat(path).st_mode), path.name)

    def test_only_private_or_loopback_clients(self):
        self.assertTrue(self.m.is_trusted_network("127.0.0.1"))
        self.assertTrue(self.m.is_trusted_network("192.168.1.5"))
        self.assertFalse(self.m.is_trusted_network("8.8.8.8"))
        self.assertFalse(self.m.is_trusted_network("not-an-ip"))


if __name__ == "__main__":
    sys.exit(0 if unittest.main(exit=False).result.wasSuccessful() else 1)
