import { ManOutlined, WomanOutlined } from '@ant-design/icons';
import { theme } from 'antd';

export function CandidateGenderIcon({ gender }: { gender: string | null | undefined }) {
  const { token } = theme.useToken();
  if (gender !== 'MALE' && gender !== 'FEMALE') return null;
  const Icon = gender === 'MALE' ? ManOutlined : WomanOutlined;
  return <Icon className="candidate-gender-icon" aria-label={gender === 'MALE' ? '男' : '女'} style={{ color: gender === 'MALE' ? token.colorInfo : token.colorError, marginInlineStart: 4, fontSize: 14 }} />;
}
