#!/usr/bin/env python3
"""Create the tiny binary AndroidManifest used by the portable APK builder.

The regular Android Studio build compiles app/src/main/AndroidManifest.xml with
aapt2. The portable builder intentionally has no Android SDK dependency, so it
emits the equivalent AXML document directly.
"""
from __future__ import annotations

import argparse
import struct
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Sequence, Tuple

NO_INDEX = 0xFFFFFFFF
ANDROID_URI = "http://schemas.android.com/apk/res/android"
ANDROID_PREFIX = "android"

RES_XML_TYPE = 0x0003
RES_STRING_POOL_TYPE = 0x0001
RES_XML_RESOURCE_MAP_TYPE = 0x0180
RES_XML_START_NAMESPACE_TYPE = 0x0100
RES_XML_END_NAMESPACE_TYPE = 0x0101
RES_XML_START_ELEMENT_TYPE = 0x0102
RES_XML_END_ELEMENT_TYPE = 0x0103

TYPE_REFERENCE = 0x01
TYPE_STRING = 0x03
TYPE_INT_DEC = 0x10
TYPE_INT_BOOLEAN = 0x12

# Stable framework resource IDs from android.R.attr.
ATTR = {
    "theme": 0x01010000,
    "label": 0x01010001,
    "name": 0x01010003,
    "exported": 0x01010010,
    "mimeType": 0x01010026,
    "minSdkVersion": 0x0101020C,
    "versionCode": 0x0101021B,
    "versionName": 0x0101021C,
    "targetSdkVersion": 0x01010270,
    "allowBackup": 0x01010280,
    "supportsRtl": 0x010103AF,
    "usesCleartextTraffic": 0x010104EC,
}


def chunk_header(kind: int, header_size: int, total_size: int) -> bytes:
    return struct.pack("<HHI", kind, header_size, total_size)


def encoded_length(value: int) -> bytes:
    if value < 0x80:
        return bytes((value,))
    if value > 0x7FFF:
        raise ValueError("AXML string is too long")
    return bytes(((value >> 8) | 0x80, value & 0xFF))


class StringPool:
    def __init__(self) -> None:
        self.values: List[str] = []
        self.indices: Dict[str, int] = {}
        self.resource_ids: Dict[int, int] = {}

    def add(self, value: Optional[str], resource_id: int = 0) -> int:
        if value is None:
            return NO_INDEX
        if value not in self.indices:
            self.indices[value] = len(self.values)
            self.values.append(value)
        index = self.indices[value]
        if resource_id:
            self.resource_ids[index] = resource_id
        return index

    def index(self, value: Optional[str]) -> int:
        if value is None:
            return NO_INDEX
        return self.indices[value]

    def build(self) -> bytes:
        offsets: List[int] = []
        payload = bytearray()
        for value in self.values:
            offsets.append(len(payload))
            utf8 = value.encode("utf-8")
            utf16_units = len(value.encode("utf-16-le")) // 2
            payload.extend(encoded_length(utf16_units))
            payload.extend(encoded_length(len(utf8)))
            payload.extend(utf8)
            payload.append(0)
        while len(payload) % 4:
            payload.append(0)
        header_size = 28
        strings_start = header_size + 4 * len(offsets)
        total_size = strings_start + len(payload)
        result = bytearray(chunk_header(RES_STRING_POOL_TYPE, header_size, total_size))
        result.extend(struct.pack("<IIIII", len(offsets), 0, 0x100, strings_start, 0))
        for offset in offsets:
            result.extend(struct.pack("<I", offset))
        result.extend(payload)
        return bytes(result)

    def build_resource_map(self) -> bytes:
        if not self.resource_ids:
            return b""
        last_index = max(self.resource_ids)
        values = [self.resource_ids.get(index, 0) for index in range(last_index + 1)]
        total_size = 8 + 4 * len(values)
        return chunk_header(RES_XML_RESOURCE_MAP_TYPE, 8, total_size) + struct.pack(
            "<" + "I" * len(values), *values
        )


@dataclass
class Attribute:
    name: str
    value: object
    namespace: Optional[str] = ANDROID_URI
    data_type: int = TYPE_STRING
    resource_id: int = 0

    def register(self, pool: StringPool) -> None:
        pool.add(self.namespace)
        pool.add(self.name, self.resource_id)
        if self.data_type == TYPE_STRING:
            pool.add(str(self.value))

    def build(self, pool: StringPool) -> bytes:
        namespace = pool.index(self.namespace)
        name = pool.index(self.name)
        if self.data_type == TYPE_STRING:
            raw = pool.index(str(self.value))
            data = raw
        else:
            raw = NO_INDEX
            data = int(self.value)
        return struct.pack("<IIIHBBI", namespace, name, raw, 8, 0, self.data_type, data)


@dataclass
class Element:
    name: str
    attributes: List[Attribute] = field(default_factory=list)
    children: List["Element"] = field(default_factory=list)

    def register(self, pool: StringPool) -> None:
        pool.add(self.name)
        for attribute in self.attributes:
            attribute.register(pool)
        for child in self.children:
            child.register(pool)


def android_string(name: str, value: str) -> Attribute:
    return Attribute(name, value, resource_id=ATTR[name])


def android_int(name: str, value: int) -> Attribute:
    return Attribute(name, value, data_type=TYPE_INT_DEC, resource_id=ATTR[name])


def android_bool(name: str, value: bool) -> Attribute:
    return Attribute(name, 1 if value else 0, data_type=TYPE_INT_BOOLEAN, resource_id=ATTR[name])


def plain_string(name: str, value: str) -> Attribute:
    return Attribute(name, value, namespace=None, resource_id=0)


def intent_filter(action: str, categories: Sequence[str], mime_type: Optional[str] = None) -> Element:
    children = [Element("action", [android_string("name", action)])]
    children.extend(Element("category", [android_string("name", category)]) for category in categories)
    if mime_type:
        children.append(Element("data", [android_string("mimeType", mime_type)]))
    return Element("intent-filter", children=children)


def manifest_tree() -> Element:
    activity = Element(
        "activity",
        [android_string("name", ".MainActivity"), android_bool("exported", True)],
        [
            intent_filter(
                "android.intent.action.MAIN",
                ["android.intent.category.LAUNCHER"],
            ),
            intent_filter(
                "android.intent.action.SEND",
                ["android.intent.category.DEFAULT"],
                "text/plain",
            ),
        ],
    )
    application = Element(
        "application",
        [
            android_string("label", "Agent Reach"),
            android_bool("allowBackup", False),
            android_bool("supportsRtl", True),
            android_bool("usesCleartextTraffic", False),
        ],
        [activity],
    )
    return Element(
        "manifest",
        [
            plain_string("package", "com.agentreach.mobile"),
            android_int("versionCode", 1),
            android_string("versionName", "1.0.0"),
        ],
        [
            Element("uses-sdk", [android_int("minSdkVersion", 26), android_int("targetSdkVersion", 35)]),
            Element("uses-permission", [android_string("name", "android.permission.INTERNET")]),
            Element("uses-permission", [android_string("name", "android.permission.ACCESS_NETWORK_STATE")]),
            application,
        ],
    )


def namespace_chunk(kind: int, pool: StringPool, line: int) -> bytes:
    prefix = pool.index(ANDROID_PREFIX)
    uri = pool.index(ANDROID_URI)
    return chunk_header(kind, 16, 24) + struct.pack("<IIII", line, NO_INDEX, prefix, uri)


def start_element(element: Element, pool: StringPool, line: int) -> bytes:
    attributes = b"".join(attribute.build(pool) for attribute in element.attributes)
    total_size = 36 + len(attributes)
    node = chunk_header(RES_XML_START_ELEMENT_TYPE, 16, total_size)
    node += struct.pack("<II", line, NO_INDEX)
    node += struct.pack(
        "<IIHHHHHH",
        NO_INDEX,
        pool.index(element.name),
        20,
        20,
        len(element.attributes),
        0,
        0,
        0,
    )
    return node + attributes


def end_element(element: Element, pool: StringPool, line: int) -> bytes:
    return (
        chunk_header(RES_XML_END_ELEMENT_TYPE, 16, 24)
        + struct.pack("<II", line, NO_INDEX)
        + struct.pack("<II", NO_INDEX, pool.index(element.name))
    )


def element_chunks(element: Element, pool: StringPool, line: List[int]) -> Iterable[bytes]:
    yield start_element(element, pool, line[0])
    line[0] += 1
    for child in element.children:
        yield from element_chunks(child, pool, line)
    yield end_element(element, pool, line[0])
    line[0] += 1


def build_manifest() -> bytes:
    tree = manifest_tree()
    pool = StringPool()
    pool.add(ANDROID_PREFIX)
    pool.add(ANDROID_URI)
    tree.register(pool)

    body = bytearray(pool.build())
    body.extend(pool.build_resource_map())
    body.extend(namespace_chunk(RES_XML_START_NAMESPACE_TYPE, pool, 1))
    body.extend(b"".join(element_chunks(tree, pool, [2])))
    body.extend(namespace_chunk(RES_XML_END_NAMESPACE_TYPE, pool, 999))
    return chunk_header(RES_XML_TYPE, 8, 8 + len(body)) + bytes(body)


def main() -> None:
    parser = argparse.ArgumentParser(description="Build Agent Reach binary AndroidManifest.xml")
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    data = build_manifest()
    args.output.write_bytes(data)
    print(f"Wrote {args.output} ({len(data)} bytes)")


if __name__ == "__main__":
    main()
