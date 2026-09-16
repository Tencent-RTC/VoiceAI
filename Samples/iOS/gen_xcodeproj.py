#!/usr/bin/env python3
"""生成 VoiceAIKitDemo.xcodeproj (iOS)

扫描 VoiceAIKitDemo/ 下的 Swift 源文件（含 Kit / Demo 子目录）+ 可选 Assets.xcassets，
写入预制的 pbxproj 模板。

VoiceAI SDK 两种接入方式（参考 https://cloud.tencent.com/document/product/647/137680）:

  方式一 CocoaPods（官方推荐）:
      python3 gen_xcodeproj.py --use-pods
      pod install
      open VoiceAIKitDemo.xcworkspace
    此模式生成的工程不手动嵌入 xcframework，改由 Podfile 中
    `pod 'TXLiteAVSDK_VoiceAI_iOS'` 负责链接。

  方式二 手动引入 xcframework（默认）:
      # 先将 TXLiteAVSDK_VoiceAI_iOS.xcframework 放到 VoiceAIKitDemo/Frameworks/
      python3 gen_xcodeproj.py
      open VoiceAIKitDemo.xcodeproj
"""

import os
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
SRC_ROOT  = os.path.join(SCRIPT_DIR, "VoiceAIKitDemo")
PROJ_DIR  = os.path.join(SCRIPT_DIR, "VoiceAIKitDemo.xcodeproj")
PROJ_FILE = os.path.join(PROJ_DIR, "project.pbxproj")

PRODUCT_NAME = "VoiceAIKitDemo"
BUNDLE_ID    = "com.tencent.voiceai.sample.ios"
FRAMEWORK    = "TXLiteAVSDK_VoiceAI_iOS.xcframework"


def walk_swift_files():
    files = []
    for root, dirs, names in os.walk(SRC_ROOT):
        if "Frameworks" in dirs:
            dirs.remove("Frameworks")
        for n in sorted(names):
            if n.endswith(".swift"):
                rel = os.path.relpath(os.path.join(root, n), SRC_ROOT)
                files.append(rel.replace(os.sep, "/"))
    return files


def has_assets_xcassets():
    return os.path.isdir(os.path.join(SRC_ROOT, "Assets.xcassets"))


def build_pbxproj(swift_files, assets_xcassets, embed_framework):
    pbx_buildfile_parts = []
    pbx_fileref_parts = []
    pbx_group_src_children_parts = []
    pbx_sources_files_parts = []

    base_uuid = "AKT0"

    for i, fpath in enumerate(swift_files):
        fid = f"{base_uuid}{i:04d}0001000100000{i:04d}"
        bid = f"{base_uuid}{i:04d}0002000200000{i:04d}"
        pbx_buildfile_parts.append(
            f'\t\t{bid} /* {fpath} in Sources */ = {{isa = PBXBuildFile; fileRef = {fid} /* {fpath} */; }};'
        )
        pbx_fileref_parts.append(
            f'\t\t{fid} /* {fpath} */ = {{isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = "{fpath}"; sourceTree = "<group>"; }};'
        )
        pbx_group_src_children_parts.append(f'\t\t\t\t{fid} /* {fpath} */,')
        pbx_sources_files_parts.append(f'\t\t\t\t{bid} /* {fpath} in Sources */,')

    # Assets.xcassets
    xcassets_buildfile_parts = []
    xcassets_fileref_parts = []
    xcassets_group_children_parts = []
    xcassets_resources_files_parts = []
    if assets_xcassets:
        xca_fid = "AKT0RXCA00100010001000000FR0"
        xca_bid = "AKT0RXCA00200020002000000FR0"
        xcassets_buildfile_parts.append(
            f'\t\t{xca_bid} /* Assets.xcassets in Resources */ = {{isa = PBXBuildFile; fileRef = {xca_fid} /* Assets.xcassets */; }};'
        )
        xcassets_fileref_parts.append(
            f'\t\t{xca_fid} /* Assets.xcassets */ = {{isa = PBXFileReference; lastKnownFileType = folder.assetcatalog; name = Assets.xcassets; path = "{PRODUCT_NAME}/Assets.xcassets"; sourceTree = SOURCE_ROOT; }};'
        )
        xcassets_group_children_parts.append(f'\t\t\t\t{xca_fid} /* Assets.xcassets */,')
        xcassets_resources_files_parts.append(f'\t\t\t\t{xca_bid} /* Assets.xcassets in Resources */,')

    main_group_id   = "AKT0000100010001F00000001"
    info_plist_id   = "AKT0000100010001F00000002"
    product_id      = "AKT0000100010001F00000003"
    frameworks_group = "AKT0000100010001F00000004"
    src_group_id    = "AKT0000100010001F00000005"
    prod_group_id   = "AKT0000100010001F00000006"

    framework_fileref_id       = "AKT0000100010001F00000007"
    framework_buildfile_id       = "AKT0000100010002F00000007"
    framework_embed_buildfile_id = "AKT0000100010002F00000008"

    resources_group_id = "AKT0R0000001000100000RG0"
    resources_phase    = "AKT0R0000004000400000RP0"

    target_id       = "AKT0000200010002F00000001"
    proj_id         = "AKT0000300010003F00000001"
    sources_phase   = "AKT0000400010004F00000001"
    frameworks_phase = "AKT0000400010004F00000002"
    embed_phase     = "AKT0000400010004F00000003"

    cfg_list_proj   = "AKT0000500010005F00000001"
    cfg_list_target = "AKT0000500010005F00000002"
    cfg_debug       = "AKT0000500010005F00000003"
    cfg_release     = "AKT0000500010005F00000004"
    cfg_debug_t     = "AKT0000500010005F00000005"
    cfg_release_t   = "AKT0000500010005F00000006"

    # --- 依据接入方式决定是否手动嵌入 xcframework ---
    if embed_framework:
        framework_buildfile_section = (
            f'\t\t{framework_buildfile_id} /* {FRAMEWORK} in Frameworks */ = {{isa = PBXBuildFile; fileRef = {framework_fileref_id} /* {FRAMEWORK} */; }};\n'
            f'\t\t{framework_embed_buildfile_id} /* {FRAMEWORK} in Embed Frameworks */ = {{isa = PBXBuildFile; fileRef = {framework_fileref_id} /* {FRAMEWORK} */; settings = {{ATTRIBUTES = (CodeSignOnCopy, RemoveHeadersOnCopy, ); }}; }};'
        )
        framework_fileref_section = (
            f'\t\t{framework_fileref_id} /* {FRAMEWORK} */ = {{isa = PBXFileReference; lastKnownFileType = wrapper.xcframework; path = "{FRAMEWORK}"; sourceTree = "<group>"; }};'
        )
        frameworks_phase_files = f'\t\t\t\t{framework_buildfile_id} /* {FRAMEWORK} in Frameworks */,'
        embed_phase_files = f'\t\t\t\t{framework_embed_buildfile_id} /* {FRAMEWORK} in Embed Frameworks */,'
        frameworks_group_children = f'\t\t\t\t{framework_fileref_id} /* {FRAMEWORK} */,'
        framework_search_paths = f'\n\t\t\t\tFRAMEWORK_SEARCH_PATHS = "$(PROJECT_DIR)/{PRODUCT_NAME}/Frameworks";'
        # 手动嵌入时需显式链接 zlib（VoiceAI SDK 依赖）
        other_ldflags = '\t\t\t\tOTHER_LDFLAGS = (\n\t\t\t\t\t"$(inherited)",\n\t\t\t\t\t"-lz",\n\t\t\t\t);'
    else:
        framework_buildfile_section = "\t\t/* VoiceAI SDK 由 CocoaPods 提供 (pod TXLiteAVSDK_VoiceAI_iOS) */"
        framework_fileref_section = ""
        frameworks_phase_files = ""
        embed_phase_files = ""
        frameworks_group_children = ""
        framework_search_paths = ""
        # zlib 及 SDK 链接参数由 Pods 的 xcconfig 通过 $(inherited) 注入
        other_ldflags = '\t\t\t\tOTHER_LDFLAGS = "$(inherited)";'

    common_settings = f"""\t\t\t\tASSETCATALOG_COMPILER_APPICON_NAME = AppIcon;
\t\t\t\tCODE_SIGN_STYLE = Automatic;
\t\t\t\tCURRENT_PROJECT_VERSION = 1;
\t\t\t\tENABLE_BITCODE = NO;
\t\t\t\tENABLE_PREVIEWS = YES;{framework_search_paths}
\t\t\t\tGENERATE_INFOPLIST_FILE = YES;
\t\t\t\tINFOPLIST_FILE = {PRODUCT_NAME}/Info.plist;
\t\t\t\tIPHONEOS_DEPLOYMENT_TARGET = 16.0;
\t\t\t\tLD_RUNPATH_SEARCH_PATHS = (
\t\t\t\t\t"$(inherited)",
\t\t\t\t\t"@executable_path/Frameworks",
\t\t\t\t);
\t\t\t\tMARKETING_VERSION = 1.0;
{other_ldflags}
\t\t\t\tPRODUCT_BUNDLE_IDENTIFIER = {BUNDLE_ID};
\t\t\t\tPRODUCT_NAME = "$(TARGET_NAME)";
\t\t\t\tSDKROOT = iphoneos;
\t\t\t\tSUPPORTED_PLATFORMS = iphoneos;
\t\t\t\tSWIFT_VERSION = 5.0;
\t\t\t\tTARGETED_DEVICE_FAMILY = "1,2";"""

    build_settings_debug = common_settings + """
\t\t\t\tSWIFT_ACTIVE_COMPILATION_CONDITIONS = DEBUG;
\t\t\t\tSWIFT_OPTIMIZATION_LEVEL = "-Onone";"""

    build_settings_release = common_settings + """
\t\t\t\tSWIFT_COMPILATION_MODE = wholemodule;
\t\t\t\tSWIFT_OPTIMIZATION_LEVEL = "-O";"""

    pbxproj = f"""// !$*UTF8*$!
{{
\tarchiveVersion = 1;
\tclasses = {{
}};
\tobjectVersion = 56;
\tobjects = {{

/* Begin PBXBuildFile section */
{chr(10).join(pbx_buildfile_parts)}
{chr(10).join(xcassets_buildfile_parts)}
{framework_buildfile_section}
/* End PBXBuildFile section */

/* Begin PBXFileReference section */
\t\t{product_id} /* {PRODUCT_NAME}.app */ = {{isa = PBXFileReference; explicitFileType = wrapper.application; path = {PRODUCT_NAME}.app; sourceTree = BUILT_PRODUCTS_DIR; }};
\t\t{info_plist_id} /* Info.plist */ = {{isa = PBXFileReference; lastKnownFileType = text.plist.xml; path = "{PRODUCT_NAME}/Info.plist"; sourceTree = "<group>"; }};
{framework_fileref_section}
{chr(10).join(pbx_fileref_parts)}
{chr(10).join(xcassets_fileref_parts)}
/* End PBXFileReference section */

/* Begin PBXFrameworksBuildPhase section */
\t\t{frameworks_phase} /* Frameworks */ = {{
\t\t\tisa = PBXFrameworksBuildPhase;
\t\t\tbuildActionMask = 2147483647;
\t\t\tfiles = (
{frameworks_phase_files}
\t\t\t);
\t\t\trunOnlyForDeploymentPostprocessing = 0;
\t\t}};
/* End PBXFrameworksBuildPhase section */

/* Begin PBXCopyFilesBuildPhase section */
\t\t{embed_phase} /* Embed Frameworks */ = {{
\t\t\tisa = PBXCopyFilesBuildPhase;
\t\t\tbuildActionMask = 2147483647;
\t\t\tdstPath = "";
\t\t\tdstSubfolderSpec = 10;
\t\t\tfiles = (
{embed_phase_files}
\t\t\t);
\t\t\tname = "Embed Frameworks";
\t\t\trunOnlyForDeploymentPostprocessing = 0;
\t\t}};
/* End PBXCopyFilesBuildPhase section */

/* Begin PBXGroup section */
\t\t{main_group_id} = {{
\t\t\tisa = PBXGroup;
\t\t\tchildren = (
\t\t\t\t{src_group_id} /* Source */,
\t\t\t\t{frameworks_group} /* Frameworks */,
\t\t\t\t{resources_group_id} /* Resources */,
\t\t\t\t{info_plist_id} /* Info.plist */,
\t\t\t\t{prod_group_id} /* Products */,
\t\t\t);
\t\t\tsourceTree = "<group>";
\t\t}};
\t\t{src_group_id} /* Source */ = {{
\t\t\tisa = PBXGroup;
\t\t\tchildren = (
{chr(10).join(pbx_group_src_children_parts)}
\t\t\t);
\t\t\tpath = {PRODUCT_NAME};
\t\t\tsourceTree = "<group>";
\t\t}};
\t\t{frameworks_group} /* Frameworks */ = {{
\t\t\tisa = PBXGroup;
\t\t\tchildren = (
{frameworks_group_children}
\t\t\t);
\t\t\tname = Frameworks;
\t\t\tpath = {PRODUCT_NAME}/Frameworks;
\t\t\tsourceTree = "<group>";
\t\t}};
\t\t{resources_group_id} /* Resources */ = {{
\t\t\tisa = PBXGroup;
\t\t\tchildren = (
{chr(10).join(xcassets_group_children_parts)}
\t\t\t);
\t\t\tname = Resources;
\t\t\tsourceTree = "<group>";
\t\t}};
\t\t{prod_group_id} /* Products */ = {{
\t\t\tisa = PBXGroup;
\t\t\tchildren = (
\t\t\t\t{product_id} /* {PRODUCT_NAME}.app */,
\t\t\t);
\t\t\tname = Products;
\t\t\tsourceTree = "<group>";
\t\t}};
/* End PBXGroup section */

/* Begin PBXNativeTarget section */
\t\t{target_id} /* {PRODUCT_NAME} */ = {{
\t\t\tisa = PBXNativeTarget;
\t\t\tbuildConfigurationList = {cfg_list_target};
\t\t\tbuildPhases = (
\t\t\t\t{sources_phase} /* Sources */,
\t\t\t\t{frameworks_phase} /* Frameworks */,
\t\t\t\t{resources_phase} /* Resources */,
\t\t\t\t{embed_phase} /* Embed Frameworks */,
\t\t\t);
\t\t\tbuildRules = (
\t\t\t);
\t\t\tdependencies = (
\t\t\t);
\t\t\tname = {PRODUCT_NAME};
\t\t\tproductName = {PRODUCT_NAME};
\t\t\tproductReference = {product_id};
\t\t\tproductType = "com.apple.product-type.application";
\t\t}};
/* End PBXNativeTarget section */

/* Begin PBXProject section */
\t\t{proj_id} /* Project object */ = {{
\t\t\tisa = PBXProject;
\t\t\tattributes = {{
\t\t\t\tBuildIndependentTargetsInParallel = 1;
\t\t\t\tLastSwiftUpdateCheck = 1600;
\t\t\t\tLastUpgradeCheck = 1600;
\t\t\t}};
\t\t\tbuildConfigurationList = {cfg_list_proj};
\t\t\tcompatibilityVersion = "Xcode 16.0";
\t\t\tmainGroup = {main_group_id};
\t\t\tproductRefGroup = {prod_group_id};
\t\t\tprojectDirPath = "";
\t\t\tprojectRoot = "";
\t\t\ttargets = (
\t\t\t\t{target_id},
\t\t\t);
\t\t}};
/* End PBXProject section */

/* Begin PBXSourcesBuildPhase section */
\t\t{sources_phase} /* Sources */ = {{
\t\t\tisa = PBXSourcesBuildPhase;
\t\t\tbuildActionMask = 2147483647;
\t\t\tfiles = (
{chr(10).join(pbx_sources_files_parts)}
\t\t\t);
\t\t\trunOnlyForDeploymentPostprocessing = 0;
\t\t}};
/* End PBXSourcesBuildPhase section */

/* Begin PBXResourcesBuildPhase section */
\t\t{resources_phase} /* Resources */ = {{
\t\t\tisa = PBXResourcesBuildPhase;
\t\t\tbuildActionMask = 2147483647;
\t\t\tfiles = (
{chr(10).join(xcassets_resources_files_parts)}
\t\t\t);
\t\t\trunOnlyForDeploymentPostprocessing = 0;
\t\t}};
/* End PBXResourcesBuildPhase section */

/* Begin XCBuildConfiguration section */
\t\t{cfg_debug} /* Debug */ = {{
\t\t\tisa = XCBuildConfiguration;
\t\t\tbuildSettings = {{
\t\t\t\tALWAYS_SEARCH_USER_PATHS = NO;
\t\t\t\tCLANG_ANALYZER_NONNULL = YES;
\t\t\t\tCOPY_PHASE_STRIP = NO;
\t\t\t\tDEBUG_INFORMATION_FORMAT = dwarf;
{build_settings_debug}
\t\t\t}};
\t\t\tname = Debug;
\t\t}};
\t\t{cfg_release} /* Release */ = {{
\t\t\tisa = XCBuildConfiguration;
\t\t\tbuildSettings = {{
\t\t\t\tALWAYS_SEARCH_USER_PATHS = NO;
\t\t\t\tCLANG_ANALYZER_NONNULL = YES;
\t\t\t\tCOPY_PHASE_STRIP = NO;
\t\t\t\tDEBUG_INFORMATION_FORMAT = "dwarf-with-dsym";
{build_settings_release}
\t\t\t}};
\t\t\tname = Release;
\t\t}};
\t\t{cfg_debug_t} /* Debug */ = {{
\t\t\tisa = XCBuildConfiguration;
\t\t\tbuildSettings = {{
{build_settings_debug}
\t\t\t\tCODE_SIGN_IDENTITY = "Apple Development";
\t\t\t\t"CODE_SIGN_IDENTITY[sdk=iphoneos*]" = "Apple Development";
\t\t\t}};
\t\t\tname = Debug;
\t\t}};
\t\t{cfg_release_t} /* Release */ = {{
\t\t\tisa = XCBuildConfiguration;
\t\t\tbuildSettings = {{
{build_settings_release}
\t\t\t\tCODE_SIGN_IDENTITY = "Apple Development";
\t\t\t\t"CODE_SIGN_IDENTITY[sdk=iphoneos*]" = "Apple Development";
\t\t\t}};
\t\t\tname = Release;
\t\t}};
/* End XCBuildConfiguration section */

/* Begin XCConfigurationList section */
\t\t{cfg_list_proj} = {{
\t\t\tisa = XCConfigurationList;
\t\t\tbuildConfigurations = (
\t\t\t\t{cfg_debug},
\t\t\t\t{cfg_release},
\t\t\t);
\t\t\tdefaultConfigurationIsVisible = 0;
\t\t\tdefaultConfigurationName = Release;
\t\t}};
\t\t{cfg_list_target} = {{
\t\t\tisa = XCConfigurationList;
\t\t\tbuildConfigurations = (
\t\t\t\t{cfg_debug_t},
\t\t\t\t{cfg_release_t},
\t\t\t);
\t\t\tdefaultConfigurationIsVisible = 0;
\t\t\tdefaultConfigurationName = Release;
\t\t}};
/* End XCConfigurationList section */

\t}};
\trootObject = {proj_id};
}}
"""
    return pbxproj


def main():
    use_pods = "--use-pods" in sys.argv[1:]
    embed_framework = not use_pods

    swift_files = walk_swift_files()
    print(f"[gen_xcodeproj] 发现 {len(swift_files)} 个 Swift 源文件")
    for f in swift_files:
        print(f"  {f}")

    assets_xcassets = has_assets_xcassets()
    print(f"[gen_xcodeproj] Assets.xcassets: {'存在' if assets_xcassets else '未找到 (跳过)'}")

    if use_pods:
        print("[gen_xcodeproj] 接入方式: CocoaPods (由 pod TXLiteAVSDK_VoiceAI_iOS 链接 SDK)")
    else:
        fw_path = os.path.join(SRC_ROOT, "Frameworks", FRAMEWORK)
        exists = os.path.isdir(fw_path)
        print(f"[gen_xcodeproj] 接入方式: 手动嵌入 xcframework ({'已找到' if exists else '未找到，请放入 Frameworks/'})")

    pbxproj = build_pbxproj(swift_files, assets_xcassets, embed_framework)
    os.makedirs(PROJ_DIR, exist_ok=True)
    with open(PROJ_FILE, "w") as f:
        f.write(pbxproj)
    print(f"[gen_xcodeproj] 已生成 {PROJ_FILE}")
    if use_pods:
        print("[gen_xcodeproj] 后续: 运行 'pod install' 后使用 'open VoiceAIKitDemo.xcworkspace'")
    else:
        print(f"[gen_xcodeproj] 后续: 使用 'open {PROJ_DIR}' 在 Xcode 中打开")


if __name__ == "__main__":
    main()
