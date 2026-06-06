# H-7 ChatViewModel 瘦身进度

## ✅ 第一步完成: 提取 ToolInstructionBuilder

**之前:** ChatViewModel.kt = 545 行  
**之后:** 
- ChatViewModel.kt = 370 行 (-175 行) ✅
- ToolInstructionBuilder.kt = 212 行 (新增)

**减少:** 32% (175/545)

## 提取内容
- toImageGenerationInstruction()
- toWebSearchInstruction()
- toLocalJsInstruction()
- toLocalJsRecoveryInstruction()
- toToolRecoveryInstruction()
- toGenericToolRecoveryInstruction()
- toFileReadRecoveryInstruction()
- toLocalJsRecoveryInstruction()
- toWebSearchRecoveryInstruction()
- toProviderConnectionRecoveryInstruction()
- toImageGenerationRecoveryInstruction()
- toTextTransformInstruction()
- toCodeDiffPreviewInstruction()
- toToolResultContinuationInstruction()
- jsonStringLiteral()

**保留在 ChatViewModel:**
- appendFileReadInstruction() (依赖 URI 参数)
- hasFileReadInstruction()
- removeFileReadInstruction()
- FILE_READ_INSTRUCTION_REGEX

## 下一步
继续提取更多逻辑，目标 < 300 行
