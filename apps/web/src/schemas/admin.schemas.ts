import { z } from "zod";

export const adminCreateUserSchema = z.object({
  username: z
    .string({ message: "请输入用户名" })
    .trim()
    .min(3, "用户名至少 3 位")
    .max(50, "用户名最多 50 位")
    .regex(/^[a-zA-Z0-9_]+$/, "用户名仅支持字母、数字和下划线"),
  password: z.string({ message: "请输入密码" }).min(8, "密码至少 8 位").max(100, "密码最多 100 位"),
  role: z.enum(["STUDENT", "TEACHER", "ADMIN"], { message: "请选择角色" })
});
