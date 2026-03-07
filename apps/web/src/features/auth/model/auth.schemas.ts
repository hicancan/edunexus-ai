import { z } from "zod";

const usernameSchema = z
  .string({ message: "请输入用户名" })
  .trim()
  .min(3, "用户名至少 3 位")
  .max(50, "用户名最多 50 位")
  .regex(/^[a-zA-Z0-9_]+$/, "用户名仅支持字母、数字和下划线");

const passwordSchema = z
  .string({ message: "请输入密码" })
  .min(8, "密码至少 8 位")
  .max(64, "密码最多 64 位");

export const loginSchema = z.object({
  username: usernameSchema,
  password: passwordSchema
});

export const registerSchema = z.object({
  username: usernameSchema,
  password: passwordSchema,
  role: z.enum(["STUDENT", "TEACHER"], { message: "请选择身份类型" }),
  email: z.union([z.string().email("邮箱格式不正确"), z.literal("")]),
  phone: z.union([z.string().regex(/^$|^[0-9\-+]{6,20}$/, "手机号格式不正确"), z.literal("")])
});

export function getFirstIssueMessage(result: { success: boolean; error?: z.ZodError }): string {
  if (result.success || !result.error) {
    return "";
  }
  return result.error.issues[0]?.message || "输入不合法";
}
