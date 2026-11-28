import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'
import { Lock, Mail, UserRound } from 'lucide-react'
import { toast } from 'sonner'
import { api } from '@/shared/api/client'
import type { AuthPayload } from '@/shared/api/types'
import { Button } from '@/shared/components/Button'
import { Input } from '@/shared/components/Input'
import { decodeJwt } from '@/shared/lib/jwt'
import { apiMessage } from '@/shared/lib/errors'
import { cn } from '@/shared/lib/cn'
import { normalizeRole, roleHome } from '@/shared/lib/roles'
import { useAuthStore } from '@/shared/stores/auth.store'
import { AuthShell } from './components/AuthShell'
import { registerSchema, type RegisterValues } from './schemas'

export function RegisterPage() {
  const navigate = useNavigate()
  const setAuth = useAuthStore((state) => state.setAuth)

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<RegisterValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: { fullName: '', email: '', password: '', confirmPassword: '', role: 'EV_OWNER' },
  })

  const registerMutation = useMutation({
    mutationFn: (values: RegisterValues) =>
      api.post<AuthPayload>('/auth/register', {
        email: values.email.trim(),
        password: values.password,
        fullName: values.fullName.trim(),
        role: values.role,
      }),
    onSuccess: (payload, values) => {
      const token = payload.accessToken ?? payload.jwt ?? payload.token
      const claims = token ? decodeJwt(token) : {}
      const role = normalizeRole(payload.roles?.[0] ?? payload.role ?? claims.role ?? values.role)
      if (token && role) {
        setAuth({
          token,
          role,
          user: {
            id: claims.sub ?? claims.userId ?? payload.userId,
            email: payload.email ?? claims.email ?? values.email,
            fullName: claims.fullName ?? claims.name ?? values.fullName,
            role,
          },
        })
        toast.success(payload.message ?? 'Đăng ký thành công')
        navigate(roleHome(role), { replace: true })
        return
      }
      toast.success(payload.message ?? 'Đăng ký thành công', {
        description: 'Vui lòng đăng nhập bằng tài khoản vừa tạo.',
      })
      navigate('/login', { replace: true })
    },
    onError: (err) => {
      toast.error('Đăng ký thất bại', { description: apiMessage(err) })
    },
  })

  return (
    <AuthShell
      title="Tạo tài khoản CarbonX"
      subtitle="Chọn vai trò của bạn để bắt đầu tham gia thị trường tín chỉ."
      footer={
        <>
          Đã có tài khoản?{' '}
          <Link to="/login" className="font-semibold text-brand-600 hover:text-brand-700">
            Đăng nhập
          </Link>
        </>
      }
    >
      <form
        noValidate
        onSubmit={handleSubmit((values) => registerMutation.mutate(values))}
        className="mt-7 space-y-4"
      >
        <Input
          label="Họ và tên"
          type="text"
          autoComplete="name"
          placeholder="Nguyễn Văn A"
          icon={<UserRound aria-hidden className="size-4" />}
          error={errors.fullName?.message}
          {...register('fullName')}
        />

        <Input
          label="Email"
          type="email"
          autoComplete="email"
          placeholder="ban@congty.vn"
          icon={<Mail aria-hidden className="size-4" />}
          error={errors.email?.message}
          {...register('email')}
        />

        <div>
          <label htmlFor="role" className="mb-1.5 block text-sm font-medium text-ink-700">
            Vai trò
          </label>
          <div className="relative">
            <UserRound aria-hidden className="pointer-events-none absolute left-3 top-3 size-4 text-ink-400" />
            <select
              id="role"
              aria-invalid={Boolean(errors.role)}
              className={cn(
                'h-11 w-full appearance-none rounded-xl border bg-white pl-10 pr-3 text-sm text-ink-900 outline-none transition',
                errors.role
                  ? 'border-rose-400 focus:border-rose-500'
                  : 'border-ink-200 focus:border-brand-500',
              )}
              {...register('role')}
            >
              <option value="EV_OWNER">Chủ xe điện (EV Owner)</option>
              <option value="COMPANY">Doanh nghiệp (Company)</option>
            </select>
          </div>
          {errors.role?.message && (
            <p role="alert" className="mt-1.5 text-xs font-medium text-rose-600">
              {errors.role.message}
            </p>
          )}
        </div>

        <Input
          label="Mật khẩu"
          type="password"
          autoComplete="new-password"
          placeholder="Tối thiểu 8 ký tự, có hoa/thường/số/ký tự đặc biệt"
          icon={<Lock aria-hidden className="size-4" />}
          error={errors.password?.message}
          {...register('password')}
        />

        <Input
          label="Xác nhận mật khẩu"
          type="password"
          autoComplete="new-password"
          placeholder="Nhập lại mật khẩu"
          icon={<Lock aria-hidden className="size-4" />}
          error={errors.confirmPassword?.message}
          {...register('confirmPassword')}
        />

        <Button type="submit" fullWidth size="lg" loading={registerMutation.isPending}>
          Tạo tài khoản
        </Button>
      </form>
    </AuthShell>
  )
}
