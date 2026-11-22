# 杨戬手臂动画

所有姿势都按真实肩→肘、肘→腕向量解释。左右骨骼的预弯不同，不能把 Euler X/Y/Z 直接当成屈肘、旋前和提携角，也不能统一套用 ±18° 侧偏。旧动画中空手常态、举臂、激光施法等即使手腕没有越界，肘仍在向后折。本版对整套动画建立左右独立的关节基准：前屈保持向角色前方、限制侧折，旋前绕真实前臂长轴进行，不再移动握点。

空手姿势统一修正反向屈肘及其过高抬肩补偿，放松手掌。持械手用受限 IK 重新分配肩肘动作，旧握点是软目标：不再为了维持不自然的旧位置强扭关节。武器仍为手骨的子节点。常规腕角限制 X ±55°、Y ±30°、Z ±25°；独立求解的雷斧接地姿势允许约 66°。

前臂先在前屈/侧偏/旋前坐标中使用 PCHIP 插值，再导出 Euler。以 60 Hz 为基础，在快速弯曲或 Euler 奇异点附近自适应增加样本，防止两个合法关键帧之间反折。肩和手腕也使用不超调曲线，护腕刚性跟随前臂。转阶段重新求解双手支撑，68～72 tick 的斧头沿整体旋转短弧落下；原本已合法的接地段保留准确落点。

游戏内 `YangJianArmKinematics` 使用相同基准。`YangJianModel` 在技能切换前先将前臂转换为物理关节角，再做混合并转换回模型角度。六个手臂关节按有限范围回正，避免最短角包裹把 +100°→−100° 绕到 180°。头部跟踪和根骨整圈旋转仍使用原逻辑。

P1、P2、P3 的 48 段动画共用加高版雕刻 `modeling/yang_jian_v4.bbmodel`。三个可编辑动作工程分别为 `yang_jian_p1.bbmodel`、`yang_jian_p2.bbmodel`、`yang_jian_p3.bbmodel`。雷斧的副手握持以实际掌心为准，枪尖与天眼位置取自同一模型。

离线生成需 Python、NumPy 和 SciPy；预览还需 Pillow。这些依赖不随模组运行。

```powershell
python tools/generate_yang_jian_animations.py
python tools/export_yang_jian_animation_project.py
python tools/export_yang_jian_p2_animation_project.py
python tools/export_yang_jian_p2_animation_project.py --phase3
python tools/test_animation_baker.py
python tools/test_yang_jian_arm_motion.py
python tools/check_yang_jian_arm_anatomy.py
python tools/validate_yang_jian_assets.py
.\gradlew.bat build
```

独立的 `check_yang_jian_arm_anatomy.py` 不调用作者工具的关节转换，通过实际旋转后的肘腕向量检查全部曲线及中点。Java 测试还验证左右基准、矩阵往返、旋前不移动握点，以及极端姿势和技能切换混合。

`tools/preview_yang_jian_hands.py --before <修改前的动画JSON>` 可用真实 mesh、UV 和骨骼变换生成前后对比；本轮修改前文件为本地 `.work/yang-arm-v2-before/runtime.json`，临时备份不进入版本库。此预览不替代游戏内实测。
